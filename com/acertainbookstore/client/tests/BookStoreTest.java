package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

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
public class BookStoreTest {

	/** The Constant TEST_ISBN. */
	private static final int TEST_ISBN = 3044560;

	/** The Constant NUM_COPIES. */
	private static final int NUM_COPIES = 5;

	/** The local test. */
	private static boolean localTest = false;

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

			if (localTest) {
				CertainBookStore store = new CertainBookStore();
				storeManager = store;
				client = store;
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
//		book = new ImmutableStockBook(isbn, "Biochemical Testing", "Kwang Zeus'", (float) 119, copies, 0, 0,
//				0, false);
//		booksToAdd.add(book);
		storeManager.addBooks(booksToAdd);
	}

	public void addBooks(int isbn, String title, String author, float price, int copies, long numSaleMisses, long numTimeRated, long totalRating, boolean editorPick) throws BookStoreException{
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook book = new ImmutableStockBook(isbn, title, author, price, copies, numSaleMisses, numTimeRated, totalRating, editorPick);
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

	//addMultipleBooks
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

	/**
	 * Tests basic buyBook() functionality.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyAllCopiesDefaultBook() throws BookStoreException {
		// Set of books to buy
		Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

		// Try to buy books
		client.buyBooks(booksToBuy);

		List<StockBook> listBooks = storeManager.getBooks();
		assertTrue(listBooks.size() == 1);
		StockBook bookInList = listBooks.get(0);
		StockBook addedBook = getDefaultBook();

		assertTrue(bookInList.getISBN() == addedBook.getISBN() && bookInList.getTitle().equals(addedBook.getTitle())
				&& bookInList.getAuthor().equals(addedBook.getAuthor()) && bookInList.getPrice() == addedBook.getPrice()
				&& bookInList.getNumSaleMisses() == addedBook.getNumSaleMisses()
				&& bookInList.getAverageRating() == addedBook.getAverageRating()
				&& bookInList.getNumTimesRated() == addedBook.getNumTimesRated()
				&& bookInList.getTotalRating() == addedBook.getTotalRating()
				&& bookInList.isEditorPick() == addedBook.isEditorPick());
	}

	@Test
	public void testRateBooks() throws BookStoreException {
		addBooks(119103, "Biochemical Testing", "Kwang Zeus'", (float) 119, 1, 2, 2, 10, false);
		addBooks(1,1);

		Set<BookRating> ratings = new HashSet<BookRating>();
		ratings.add(new BookRating(TEST_ISBN, 3));
		ratings.add(new BookRating(119103, 5));

		client.rateBooks(ratings);

		List<StockBook> listBooks = storeManager.getBooks();
		HashMap<Integer, Integer> numTimeRatedMap = new HashMap<>();
		numTimeRatedMap.put(119103, 3);
		numTimeRatedMap.put(TEST_ISBN, 1);
		numTimeRatedMap.put(1, 0);
		HashMap<Integer, Integer> totalRateMap = new HashMap<>();
		totalRateMap.put(119103, 15);
		totalRateMap.put(TEST_ISBN, 3);
		totalRateMap.put(1, 0);

		for (StockBook book : listBooks) {
			int isbn = book.getISBN();
			int num_rate = numTimeRatedMap.get(isbn).intValue();
			int total = totalRateMap.get(isbn).intValue();
			assertTrue(num_rate == book.getNumTimesRated() && total == book.getTotalRating());
		}
	}

	@Test
	public void testOutOfRangeRateBooks() throws BookStoreException {
		addBooks(119103, "Biochemical Testing", "Kwang Zeus'", (float) 119, 1, 2, 2, 10, false);
		addBooks(1,1);

		Set<BookRating> ratings = new HashSet<BookRating>();
		ratings.add(new BookRating(TEST_ISBN, 3));
		ratings.add(new BookRating(119103, -5));

		try {
			client.rateBooks(ratings);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> listBooks = storeManager.getBooks();
		HashMap<Integer, Integer> numTimeRatedMap = new HashMap<>();
		numTimeRatedMap.put(119103, 2);
		numTimeRatedMap.put(TEST_ISBN, 0);
		numTimeRatedMap.put(1, 0);
		HashMap<Integer, Integer> totalRateMap = new HashMap<>();
		totalRateMap.put(119103, 10);
		totalRateMap.put(TEST_ISBN, 0);
		totalRateMap.put(1, 0);

		for (StockBook book : listBooks) {
			int isbn = book.getISBN();
			int num_rate = numTimeRatedMap.get(isbn).intValue();
			int total = totalRateMap.get(isbn).intValue();
			assertTrue(num_rate == book.getNumTimesRated() && total == book.getTotalRating());
		}
	}

	@Test
	public void testInvalidISBNRateBooks() throws BookStoreException {
		addBooks(119103, "Biochemical Testing", "Kwang Zeus'", (float) 119, 1, 2, 2, 10, false);
		addBooks(1,1);

		Set<BookRating> ratings = new HashSet<BookRating>();
		ratings.add(new BookRating(TEST_ISBN, 3));
		ratings.add(new BookRating(-1, 5));

		try {
			client.rateBooks(ratings);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> listBooks = storeManager.getBooks();
		HashMap<Integer, Integer> numTimeRatedMap = new HashMap<>();
		numTimeRatedMap.put(119103, 2);
		numTimeRatedMap.put(TEST_ISBN, 0);
		numTimeRatedMap.put(1, 0);
		HashMap<Integer, Integer> totalRateMap = new HashMap<>();
		totalRateMap.put(119103, 10);
		totalRateMap.put(TEST_ISBN, 0);
		totalRateMap.put(1, 0);

		for (StockBook book : listBooks) {
			int isbn = book.getISBN();
			int num_rate = numTimeRatedMap.get(isbn).intValue();
			int total = totalRateMap.get(isbn).intValue();
			assertTrue(num_rate == book.getNumTimesRated() && total == book.getTotalRating());
		}
	}

	@Test
	public void testMultipleRateBook() throws BookStoreException {

		int N = 10;
		float avg = 0.0f;
		long sum = 0;
		ArrayList<Integer> rates = new ArrayList<>(N);
		for (int i = 0; i < N; i++) {
			int random = ThreadLocalRandom.current().nextInt(0, 6);
			rates.add(random);
			avg += random;
			sum += random;
		}
		avg /= N;

		for (int i : rates) {
			Set<BookRating> ratings = new HashSet<>();
			ratings.add(new BookRating(TEST_ISBN, i));
			client.rateBooks(ratings);
		}

		List<StockBook> listBooks = storeManager.getBooks();
		HashMap<Integer, Integer> numTimeRatedMap = new HashMap<>();
		numTimeRatedMap.put(TEST_ISBN, N);
		HashMap<Integer, Long> totalRateMap = new HashMap<>();
		totalRateMap.put(TEST_ISBN, sum);
		HashMap<Integer, Float> avgRateMap = new HashMap<>();
		avgRateMap.put(TEST_ISBN, avg);

		for (StockBook book : listBooks) {
			int isbn = book.getISBN();
			int num_rate = numTimeRatedMap.get(isbn).intValue();
			int total = totalRateMap.get(isbn).intValue();
			float avg_ = avgRateMap.get(isbn).floatValue();
			assertTrue(num_rate == book.getNumTimesRated() && total == book.getTotalRating() && avg_ == book.getAverageRating());
		}
	}

	//testGetTopK
	@Test
	public void testGetTopK() throws BookStoreException {
		addBooks(119103, "Biochemical Testing", "Kwang Zeus'", (float) 119, 1, 2, 2, 10, false);
		addBooks(1,1);

		Set<BookRating> ratings = new HashSet<BookRating>();
		ratings.add(new BookRating(TEST_ISBN, 3));
		ratings.add(new BookRating(119103, 5));
		ratings.add(new BookRating(1, 4));

		client.rateBooks(ratings);
		int K = 2;
		List<Book> listBooks = client.getTopRatedBooks(K);
		List<Integer> isbns = new ArrayList<>();
		isbns.add(119103);
		isbns.add(1);

		for (int i = 0; i < 2; i++) {
			int isbn = listBooks.get(i).getISBN();
			int ref_isbn = isbns.get(i);
			assertTrue(isbn == ref_isbn);
		}
	}
	//testGetTopK: invalid (K<1)
	@Test
	public void testOutOfRangeGetTopK() throws BookStoreException {
		addBooks(119103, "Biochemical Testing", "Kwang Zeus'", (float) 119, 1, 2, 2, 10, false);
		addBooks(1,1);

		Set<BookRating> ratings = new HashSet<BookRating>();
		ratings.add(new BookRating(TEST_ISBN, 3));
		ratings.add(new BookRating(119103, 5));
		ratings.add(new BookRating(1, 4));

		client.rateBooks(ratings);
		int K = -2;
		try {
			List<Book> listBooks = client.getTopRatedBooks(K);
			fail();
		}
		catch (BookStoreException ex) {
			;
		}
	}
	//testGetTopK: K > size
	@Test
	public void testLargerKGetTopK() throws BookStoreException {
		addBooks(119103, "Biochemical Testing", "Kwang Zeus'", (float) 119, 1, 2, 2, 10, false);
		addBooks(1,1);

		Set<BookRating> ratings = new HashSet<BookRating>();
		ratings.add(new BookRating(TEST_ISBN, 3));
		ratings.add(new BookRating(119103, 5));
		ratings.add(new BookRating(1, 4));

		client.rateBooks(ratings);
		int K = 5;
		List<Book> listBooks = client.getTopRatedBooks(K);

		assertTrue(listBooks.size() == 3 && listBooks.containsAll(storeManager.getBooks()));
	}

	/**
	 * Tests that books with invalid ISBNs cannot be bought.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyInvalidISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with invalid ISBN.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(-1, 1)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that books can only be bought if they are in the book store.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNonExistingISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with ISBN which does not exist.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(100000, 10)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy more books than there are copies.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyTooManyBooks() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy more copies than there are in store.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy a negative number of books.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a negative number of copies.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that all books can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetBooks() throws BookStoreException {
		Set<StockBook> booksAdded = new HashSet<StockBook>();
		booksAdded.add(getDefaultBook());

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		booksAdded.addAll(booksToAdd);

		storeManager.addBooks(booksToAdd);

		// Get books in store.
		List<StockBook> listBooks = storeManager.getBooks();

		// Make sure the lists equal each other.
		assertTrue(listBooks.containsAll(booksAdded) && listBooks.size() == booksAdded.size());
	}

	/**
	 * Tests that a list of books with a certain feature can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetCertainBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		storeManager.addBooks(booksToAdd);

		// Get a list of ISBNs to retrieved.
		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);

		// Get books with that ISBN.
		List<Book> books = client.getBooks(isbnList);

		// Make sure the lists equal each other
		assertTrue(books.containsAll(booksToAdd) && books.size() == booksToAdd.size());
	}

	/**
	 * Tests that books cannot be retrieved if ISBN is invalid.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetInvalidIsbn() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Make an invalid ISBN.
		HashSet<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN); // valid
		isbnList.add(-1); // invalid

		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.getBooks(isbnList);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
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
