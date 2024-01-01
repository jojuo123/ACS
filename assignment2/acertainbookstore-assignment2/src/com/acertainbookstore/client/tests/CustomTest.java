package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.acertainbookstore.business.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * {@link BookStoreTest} tests the {@link BookStore} interface.
 *
 * @see BookStore
 */
public class CustomTest {

    /** The Constant TEST_ISBN. */
    private static final int TEST_ISBN = 3044560;

    /** The Constant NUM_COPIES. */
    private static final int NUM_COPIES = 5;

    /** The local test. */
    private static boolean localTest = true;

    /** Single lock test */
    private static boolean singleLock = false;


    /** The store manager. */
    private static StockManager storeManager;

    /** The client. */
    private static BookStore client;

    /**
     * Sets the up before class.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        try {
            String localTestProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
            localTest = (localTestProperty != null) ? Boolean.parseBoolean(localTestProperty) : localTest;

            String singleLockProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_SINGLE_LOCK);
            singleLock = (singleLockProperty != null) ? Boolean.parseBoolean(singleLockProperty) : singleLock;

            if (localTest) {
                if (singleLock) {
                    SingleLockConcurrentCertainBookStore store = new SingleLockConcurrentCertainBookStore();
                    storeManager = store;
                    client = store;
                } else {
                    TwoLevelLockingConcurrentCertainBookStore store = new TwoLevelLockingConcurrentCertainBookStore();
                    storeManager = store;
                    client = store;
                }
            } else {
                storeManager = new StockManagerHTTPProxy("http://localhost:8081/stock");
                client = new BookStoreHTTPProxy("http://localhost:8081");
            }

            storeManager.removeAllBooks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method to add some books.
     *
     * @param isbn
     *            the isbn
     * @param copies
     *            the copies
     * @throws BookStoreException
     *             the book store exception
     */
    public void addBooks(int isbn, int copies) throws BookStoreException {
        Set<StockBook> booksToAdd = new HashSet<StockBook>();
        StockBook book = new ImmutableStockBook(isbn, "Test of Thrones", "George RR Testin'", (float) 10, copies, 0, 0,
                0, false);
        booksToAdd.add(book);
        storeManager.addBooks(booksToAdd);
    }

    /**
     * Helper method to get the default book used by initializeBooks.
     *
     * @return the default book
     */
    public StockBook getDefaultBook() {
        return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit", "JK Unit", (float) 10, NUM_COPIES, 0, 0, 0,
                false);
    }

    /**
     * Method to add a book, executed before every test case is run.
     *
     * @throws BookStoreException
     *             the book store exception
     */
    @Before
    public void initializeBooks() throws BookStoreException {
        Set<StockBook> booksToAdd = new HashSet<StockBook>();
        booksToAdd.add(getDefaultBook());
        storeManager.addBooks(booksToAdd);
    }

    /**
     * Method to clean up the book store, execute after every test case is run.
     *
     * @throws BookStoreException
     *             the book store exception
     */
    @After
    public void cleanupBooks() throws BookStoreException {
        storeManager.removeAllBooks();
    }

    //test 1: check buy + copy concurrency
    @Test
    public void testConcurrentAddCopiesDefaultBook() throws BookStoreException, InterruptedException {

        Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
        booksToBuy.add(new BookCopy(TEST_ISBN, 1));
        Set<BookCopy> booksToCopy = new HashSet<>();
        booksToCopy.add(new BookCopy(TEST_ISBN, 1));

        class BuyBooks implements Runnable {
            public void run() {
                System.out.println(Thread.currentThread().getName() + " buying books");
                for (int i = 0; i < NUM_COPIES-1; ++i) {
                    try {
                        client.buyBooks(booksToBuy);
                    } catch (BookStoreException e) {
                        ;
                    }
                }
            }
        }

        class CopyBooks implements Runnable {
            public void run() {
                System.out.println(Thread.currentThread().getName() + " copying books");
                for (int i = 0; i < NUM_COPIES-1; ++i) {
                    try {
                        storeManager.addCopies(booksToCopy);
                    } catch (BookStoreException e) {
                        ;
                    }
                }
            }
        }

        Thread t1 = new Thread(new BuyBooks());
        Thread t2 = new Thread(new CopyBooks());
        t1.setName("buyBooks");
        t2.setName("copyBooks");

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        List<StockBook> stockBooks = storeManager.getBooks();
        assertEquals(1, stockBooks.size());
        assertEquals(NUM_COPIES, stockBooks.get(0).getNumCopies());
    }

    //check if adding + buying concurrency is before-or-after
    @Test
    public void testConcurrentAddUpdate() throws BookStoreException, InterruptedException {

        Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
        booksToBuy.add(new BookCopy(TEST_ISBN, 1));
//		storeManager.addCopies(booksToCopy);

        class AddBooks implements Runnable {
            public void run() {
                System.out.println(Thread.currentThread().getName() + " adding books");
                for (int i = 0; i < 15; ++i) {
                    try {
                        Set<StockBook> booksToAdd = new HashSet<StockBook>();
                        StockBook book = new ImmutableStockBook(TEST_ISBN+i+1, "Test of Thrones", "George RR Testin'", (float) 10, 10, 0, 0,
                                0, false);
                        booksToAdd.add(book);
                        storeManager.addBooks(booksToAdd);
                    } catch (BookStoreException e) {
                        ;
                    }
                }
            }
        }

        class BuyBooks implements Runnable {
            public void run() {
                System.out.println(Thread.currentThread().getName() + " buying books");
                for (int i = 0; i < NUM_COPIES-1; ++i) {
                    try {
                        client.buyBooks(booksToBuy);
                    } catch (BookStoreException e) {
                        ;
                    }
                }
            }
        }

        Thread t1 = new Thread(new AddBooks());
        Thread t2 = new Thread(new BuyBooks());
        t1.setName("addBooks");
        t2.setName("buyBooks");

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        // Check if after update and inserts, we have 15 + 1 books and the proper ammound of books has been sold
        List<StockBook> stockBooks = storeManager.getBooks();
        assertEquals(16, stockBooks.size());
        for (StockBook b: stockBooks){
            if(b.getISBN() == TEST_ISBN)
                assertEquals(1, b.getNumCopies());
        }
    }

    //test if adding and deletion all is before-or-after
    @Test
    public void testConcurrentAddDelete() throws BookStoreException, InterruptedException {
        int initial = 10;
        // Initialize with some books
        for (int i = 0; i < initial; ++i) {
            try {
                Set<StockBook> booksToAdd = new HashSet<StockBook>();
                StockBook book = new ImmutableStockBook(TEST_ISBN+i+1, "Test of Thrones", "George RR Testin'", (float) 10, 10, 0, 0,
                        0, false);
                booksToAdd.add(book);
                storeManager.addBooks(booksToAdd);
            } catch (BookStoreException e) {
                ;
            }
        }

        class AddBooks implements Runnable {
            public void run() {
                System.out.println(Thread.currentThread().getName() + " adding books");
                for (int i = 0; i < initial; ++i) {
                    try {
                        Set<StockBook> booksToAdd = new HashSet<StockBook>();
                        StockBook book = new ImmutableStockBook(TEST_ISBN+i+initial+1, "Test of Thrones", "George RR Testin'", (float) 10, 10, 0, 0,
                                0, false);
                        booksToAdd.add(book);
                        storeManager.addBooks(booksToAdd);
                    } catch (BookStoreException e) {
                        ;
                    }
                }
            }
        }

        class DeleteBooks implements Runnable {
            public void run() {
                System.out.println(Thread.currentThread().getName() + " removing all books");
                for (int i = 0; i < initial-2; ++i) {
                    try {
                        storeManager.removeAllBooks();
                    } catch (BookStoreException e) {
                        ;
                    }
                }
            }
        }

        Thread t1 = new Thread(new AddBooks());
        Thread t2 = new Thread(new DeleteBooks());
        t1.setName("addBooks");
        t2.setName("removeBooks");

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        List<StockBook> stockBooks = storeManager.getBooks();
        assertTrue(stockBooks.size() <= initial);
    }


    //test: both T1 and T2 update editor choice (with overlapped isbns)
    @Test
    public void testConcurrentMultipleEditorPicks() throws BookStoreException, InterruptedException {
        int initialAddedBooks = 10;
        // Initialize with some books
        for (int i = 0; i < initialAddedBooks; ++i) {
            try {
                Set<StockBook> booksToAdd = new HashSet<StockBook>();
                StockBook book = new ImmutableStockBook(TEST_ISBN+i+1, "Test of Thrones", "George RR Testin'", (float) 10, 10, 0, 0,
                        0, false);
                booksToAdd.add(book);
                storeManager.addBooks(booksToAdd);
            } catch (BookStoreException e) {
                ;
            }
        }

        class Editor1 implements Runnable {
            public void run() {
                System.out.println(Thread.currentThread().getName() + " editing books");
                for (int i = 0; i < 5; ++i) {
                    try {

                        Set<BookEditorPick> editorPicksVals = new HashSet<BookEditorPick>();
                        BookEditorPick editorPick = new BookEditorPick(TEST_ISBN+i+1, true);
                        editorPicksVals.add(editorPick);
                        storeManager.updateEditorPicks(editorPicksVals);
                    } catch (BookStoreException e) {
                        ;
                    }
                }
            }
        }

        class Editor2 implements Runnable {
            public void run() {
                System.out.println(Thread.currentThread().getName() + " editing books");
                for (int i = 3; i <10; ++i) {
                    try {

                        Set<BookEditorPick> editorPicksVals = new HashSet<BookEditorPick>();
                        BookEditorPick editorPick = new BookEditorPick(TEST_ISBN+i+1, true);
                        editorPicksVals.add(editorPick);
                        storeManager.updateEditorPicks(editorPicksVals);
                    } catch (BookStoreException e) {
                        ;
                    }
                }
            }
        }


        Thread t1 = new Thread(new Editor1());
        Thread t2 = new Thread(new Editor2());
        t1.setName("edit1");
        t2.setName("edit2");

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        // get all picks
        List<Book> books = client.getEditorPicks(12);
        Set<Integer> set_isbn = new HashSet<>();
        set_isbn = books.stream().map(Book::getISBN).collect(Collectors.toSet());
        Set<Integer> expected = new HashSet<>();
        for (int i = TEST_ISBN+1; i < TEST_ISBN+1 + 10; ++i) {
            expected.add(i);
        }
        assertEquals(expected, set_isbn);
        assertEquals(books.size(), 10);
    }

    //test deleting and getting is all-or-nothing and before-or-after
    @Test
    public void testConcurrentDeleteGet() throws BookStoreException, InterruptedException {

        Set<Integer> isbns = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            isbns.add(TEST_ISBN+i);
        }

        int initial = 10;
        // Initialize with some books
        for (int i = 0; i < initial; ++i) {
            try {
                Set<StockBook> booksToAdd = new HashSet<StockBook>();
                StockBook book = new ImmutableStockBook(TEST_ISBN+i+1, "Test of Thrones", "George RR Testin'", (float) 10, 10, 0, 0,
                        0, false);
                booksToAdd.add(book);
                storeManager.addBooks(booksToAdd);
            } catch (BookStoreException e) {
                ;
            }
        }

        class DeleteBooks implements Runnable {
            public void run() {
                try {
                    storeManager.removeBooks(isbns);
                } catch (BookStoreException e) {
                    ;
                }
            }
        }
        valid = true;
        class GetBooks implements Runnable {
            public void run() {
                try {
                    List<Book> list_books = client.getBooks(isbns);
                    if (list_books.size() != 0 && list_books.size() != isbns.size())
                        valid = false;
                } catch (BookStoreException e) {
                    valid = true;
                    ;
                }
            }
        }

        Thread t1 = new Thread(new DeleteBooks());
        Thread t2 = new Thread(new GetBooks());
        t1.setName("deleteBooks");
        t2.setName("getBooks");

        t1.start();
        t2.start();

        t1.join();
        t2.join();

//        List<StockBook> stockBooks = storeManager.getBooks();
        assertTrue(valid);
    }

    volatile boolean valid = true;
    //test 2: T1 buy + copy, T2 getbooks (T2 should get the original size or size - no_of_bought_books)
    @Test
    public void testConcurencyBuyCopyGet() throws BookStoreException, InterruptedException{

        Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
        booksToBuy.add(new BookCopy(TEST_ISBN, 2));
        Set<BookCopy> booksToCopy = new HashSet<>();
        booksToCopy.add(new BookCopy(TEST_ISBN, 2));
        class Client1 implements Runnable {
            public void run() {
                System.out.println(Thread.currentThread().getName() + " buying books");
                for (int i = 0; i < 60; ++i) {
                    try {
                        client.buyBooks(booksToBuy);
                        storeManager.addCopies(booksToCopy);
                    } catch (BookStoreException e) {
                        ;
                    }
                }
            }
        }
        valid = true;
        class Client2 implements Runnable {
            public void run() {
                System.out.println(Thread.currentThread().getName() + " get books");
                for (int i = 0; i < 60; ++i) {
                    try {
                        List<StockBook> listBooks = storeManager.getBooks();
                        int k = listBooks.get(0).getNumCopies();
//                        System.out.println("num books " + k);
                        if (k != NUM_COPIES && k != NUM_COPIES - 2) {
                            valid = false;
                        }
                    } catch (BookStoreException e) {
                        ;
                    }
                }
            }
        }

        Thread t1 = new Thread(new Client1());
        Thread t2 = new Thread(new Client2());
        t1.setName("buyBooks");
        t2.setName("copyBooks");

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        assertTrue(valid);
    }

    /**
     * Tear down after class.
     *
     * @throws BookStoreException
     *             the book store exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws BookStoreException {
        storeManager.removeAllBooks();

        if (!localTest) {
            ((BookStoreHTTPProxy) client).stop();
            ((StockManagerHTTPProxy) storeManager).stop();
        }
    }
}
