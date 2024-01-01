package com.acertainbookstore.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.utils.BookStoreUtility;


/** {@link TwoLevelLockingConcurrentCertainBookStore} implements the {@link BookStore} and
 * {@link StockManager} functionalities.
 * 
 * @see BookStore
 * @see StockManager
 */
public class TwoLevelLockingConcurrentCertainBookStore implements BookStore, StockManager {

	/** The mapping of books from ISBN to {@link BookStoreBook}. */
	private Map<Integer, BookStoreBook> bookMap = null;
	private Map<Integer, ReentrantReadWriteLock> itemLocksMap = null;
	private final ReentrantReadWriteLock listLock = new ReentrantReadWriteLock(true);

	/**
	 * Instantiates a new {@link CertainBookStore}.
	 */
	public TwoLevelLockingConcurrentCertainBookStore() {
		// Constructors are not synchronized
		bookMap = new HashMap<>();
		itemLocksMap = new HashMap<>();
	}
	
	private void validate(StockBook book) throws BookStoreException {
		int isbn = book.getISBN();
		String bookTitle = book.getTitle();
		String bookAuthor = book.getAuthor();
		int noCopies = book.getNumCopies();
		float bookPrice = book.getPrice();

		if (BookStoreUtility.isInvalidISBN(isbn)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookTitle)) { // Check if the book has valid title
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookAuthor)) { // Check if the book has valid author
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isInvalidNoCopies(noCopies)) { // Check if the book has at least one copy
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookPrice < 0.0) { // Check if the price of the book is valid
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookMap.containsKey(isbn)) {// Check if the book is not in stock
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.DUPLICATED);
		}
	}	
	
	private void validate(BookCopy bookCopy) throws BookStoreException {

		int isbn = bookCopy.getISBN();
		int numCopies = bookCopy.getNumCopies();
		try {
			validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock
		} catch (BookStoreException e) {
//			itemLocksMap.get(isbn);
			throw e;
		}

		if (BookStoreUtility.isInvalidNoCopies(numCopies)) { // Check if the number of the book copy is larger than zero
//			itemLocksMap.get(isbn).readLock().unlock();
			throw new BookStoreException(BookStoreConstants.NUM_COPIES + numCopies + BookStoreConstants.INVALID);
		}
//		itemLocksMap.get(isbn).readLock().unlock();
	}
	
	private void validate(BookEditorPick editorPickArg) throws BookStoreException {
		int isbn = editorPickArg.getISBN();
		try {
			validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock
		} catch (BookStoreException e) {
			throw e;
		}
	}
	
	private void validateISBNInStock(Integer ISBN) throws BookStoreException {
		if (BookStoreUtility.isInvalidISBN(ISBN)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
		}
		if (!bookMap.containsKey(ISBN)) {// Check if the book is in stock
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addBooks(java.util.Set)
	 */
	public void addBooks(Set<StockBook> bookSet) throws BookStoreException {
		if (bookSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		//get the lock for the whole database
		listLock.writeLock().lock();
		// Check if all are there
		for (StockBook book : bookSet) {
			try {
				validate(book);
			} catch (BookStoreException e) {
				//release if exception found
				listLock.writeLock().unlock();
				throw e;
			}
		}

		for (StockBook book : bookSet) {
			int isbn = book.getISBN();
			bookMap.put(isbn, new BookStoreBook(book));
			itemLocksMap.put(isbn, new ReentrantReadWriteLock(true));
		}
		//release in the end
		listLock.writeLock().unlock();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addCopies(java.util.Set)
	 */
	//
	public void addCopies(Set<BookCopy> bookCopiesSet) throws BookStoreException {
		int isbn;
		boolean flag = false;
		int numCopies;

		if (bookCopiesSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		//only update specific item -> use read lock as intention lock
		listLock.readLock().lock();
		List<Integer> lock_list = new ArrayList<>(); //list of items get locked
		for (BookCopy bookCopy : bookCopiesSet) {
			isbn = bookCopy.getISBN();
			try {
				if (itemLocksMap.containsKey(isbn)) {
					//acquire lock for each valid item
					itemLocksMap.get(isbn).writeLock().lock();
					lock_list.add(isbn);
					validate(bookCopy);
				} else {
					throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.INVALID);
				}
			} catch (BookStoreException e) {
				//release all the locks if exception found
				for (int i : lock_list)
					itemLocksMap.get(i).writeLock().unlock();
				listLock.readLock().unlock();
				throw e;
			}
		}

//		System.out.println("copy finish validation");
		BookStoreBook book;

		// Update the number of copies
		for (BookCopy bookCopy : bookCopiesSet) {
			isbn = bookCopy.getISBN();
			numCopies = bookCopy.getNumCopies();
			book = bookMap.get(isbn);
			book.addCopies(numCopies);
			//release immediately after modification
			itemLocksMap.get(isbn).writeLock().unlock();
		}
		listLock.readLock().unlock();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooks()
	 */
	public List<StockBook> getBooks() {
		listLock.readLock().lock();
		List<StockBook> ret = new ArrayList<>();
		for (int isbn : bookMap.keySet())
		{
			itemLocksMap.get(isbn).readLock().lock();
			ret.add(bookMap.get(isbn).immutableStockBook());
		}
		//release the lock after reading
		for (int isbn : bookMap.keySet())
			itemLocksMap.get(isbn).readLock().unlock();

		listLock.readLock().unlock();
		return ret;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#updateEditorPicks(java.util
	 * .Set)
	 */
	public void updateEditorPicks(Set<BookEditorPick> editorPicks) throws BookStoreException {
		// Check that all ISBNs that we add/remove are there first.
		if (editorPicks == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		listLock.readLock().lock();
		int isbnValue;
		List<Integer> lock_list = new ArrayList<>(); //list of needed locks

		for (BookEditorPick editorPickArg : editorPicks) {
			isbnValue = editorPickArg.getISBN();
			try {

				if (itemLocksMap.containsKey(isbnValue)) {
					itemLocksMap.get(isbnValue).writeLock().lock();
					lock_list.add(isbnValue);
					validate(editorPickArg);
				} else {
					throw new BookStoreException(BookStoreConstants.ISBN + isbnValue + BookStoreConstants.INVALID);
				}
			} catch (BookStoreException e) {
				//release if fail
				for (int isbn : lock_list)
					itemLocksMap.get(isbn).writeLock().unlock();
				listLock.readLock().unlock();
				throw e;
			}
		}

		for (BookEditorPick editorPickArg : editorPicks) {
			isbnValue = editorPickArg.getISBN();
			bookMap.get(editorPickArg.getISBN()).setEditorPick(editorPickArg.isEditorPick());
			//release the lock immediately
			itemLocksMap.get(isbnValue).writeLock().unlock();
		}
		listLock.readLock().unlock();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#buyBooks(java.util.Set)
	 */
	public void buyBooks(Set<BookCopy> bookCopiesToBuy) throws BookStoreException {
		if (bookCopiesToBuy == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		// Check that all ISBNs that we buy are there first.
		int isbn = -1;
		boolean flag = false;
		BookStoreBook book;
		Boolean saleMiss = false;

		Map<Integer, Integer> salesMisses = new HashMap<>();
		listLock.readLock().lock();
		List<Integer> lock_list = new ArrayList<>();
		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {

			isbn = bookCopyToBuy.getISBN();
			try {

				if (itemLocksMap.containsKey(isbn)) {
					//acquire the lock on items
					itemLocksMap.get(isbn).writeLock().lock();
					lock_list.add(isbn);
					validate(bookCopyToBuy);
					book = bookMap.get(isbn);

					if (!book.areCopiesInStore(bookCopyToBuy.getNumCopies())) {
						// If we cannot sell the copies of the book, it is a miss.
						salesMisses.put(isbn, bookCopyToBuy.getNumCopies() - book.getNumCopies());
						saleMiss = true;
					}
				} else {
					throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.INVALID);
				}
			} catch (BookStoreException e) {
				//release if fail
				for (int i : lock_list)
					itemLocksMap.get(i).writeLock().unlock();
				listLock.readLock().unlock();
				throw e;
			}
		}
		// We throw exception now since we want to see how many books in the
		// order incurred misses which is used by books in demand
		if (saleMiss) {
			for (Map.Entry<Integer, Integer> saleMissEntry : salesMisses.entrySet()) {
				isbn = saleMissEntry.getKey();
				book = bookMap.get(saleMissEntry.getKey());
				book.addSaleMiss(saleMissEntry.getValue());
				lock_list.remove(Integer.valueOf(isbn));
				//release on missed item
				itemLocksMap.get(isbn).writeLock().unlock();
			}
			//release other if has a miss sale
			for (int i : lock_list)
				itemLocksMap.get(i).writeLock().unlock();
			listLock.readLock().unlock();
			throw new BookStoreException(BookStoreConstants.BOOK + BookStoreConstants.NOT_AVAILABLE);
		}

		// Then make the purchase.
		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
			isbn = bookCopyToBuy.getISBN();
			book = bookMap.get(bookCopyToBuy.getISBN());
			book.buyCopies(bookCopyToBuy.getNumCopies());
			//release the lock on item as soon as it is done
			itemLocksMap.get(isbn).writeLock().unlock();
		}
		listLock.readLock().unlock();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#getBooksByISBN(java.util.
	 * Set)
	 */
	public List<StockBook> getBooksByISBN(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		listLock.readLock().lock();
		List<Integer> lock_list = new ArrayList<>();
		for (Integer ISBN : isbnSet) {
			try {
				if (itemLocksMap.containsKey(ISBN)) {
					itemLocksMap.get(ISBN).readLock().lock();
					lock_list.add(ISBN);
					validateISBNInStock(ISBN);
				} else {
					throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
				}
			} catch (BookStoreException e) {
				//releas if fail
				for (int i : lock_list)
					itemLocksMap.get(i).readLock().unlock();
				listLock.readLock().unlock();
				throw e;
			}
		}

		List<StockBook> ret = new ArrayList<>();
		for (int isbn : isbnSet) {
			ret.add(bookMap.get(isbn).immutableStockBook());
			//release as soon as possible
			itemLocksMap.get(isbn).readLock().lock();
		}

		listLock.readLock().lock();
		return ret;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getBooks(java.util.Set)
	 */
	public List<Book> getBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		listLock.readLock().lock();
		List<Integer> lock_list = new ArrayList<>();
		for (Integer ISBN : isbnSet) {
			try {
				if (itemLocksMap.containsKey(ISBN)) {
					itemLocksMap.get(ISBN).readLock().lock();
					lock_list.add(ISBN);
					validateISBNInStock(ISBN);
				} else {
					throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
				}
			} catch (BookStoreException e) {
				//release if fail
				for (int i : lock_list)
					itemLocksMap.get(i).readLock().unlock();
				listLock.readLock().unlock();
				throw e;
			}
		}

		// Check that all ISBNs that we rate are there to start with.
		List<Book> ret = new ArrayList<>();
		for (int isbn : isbnSet) {
			ret.add(bookMap.get(isbn).immutableBook());
			//release as soon as possible
			itemLocksMap.get(isbn).readLock().lock();
		}
		listLock.readLock().unlock();
		return ret;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getEditorPicks(int)
	 */
	public List<Book> getEditorPicks(int numBooks) throws BookStoreException {
		if (numBooks < 0) {
			throw new BookStoreException("numBooks = " + numBooks + ", but it must be positive");
		}

		listLock.readLock().lock();
		List<BookStoreBook> listAllEditorPicks = new ArrayList<>();

		for (int isbn : bookMap.keySet()) {
			itemLocksMap.get(isbn).readLock().lock();
			if(bookMap.get(isbn).isEditorPick())
				listAllEditorPicks.add(bookMap.get(isbn));
		}

		// Find numBooks random indices of books that will be picked.
		Random rand = new Random();
		Set<Integer> tobePicked = new HashSet<>();
		int rangePicks = listAllEditorPicks.size();

		if (rangePicks <= numBooks) {

			// We need to add all books.
			for (int i = 0; i < listAllEditorPicks.size(); i++) {
				tobePicked.add(i);
			}
		} else {

			// We need to pick randomly the books that need to be returned.
			int randNum;

			while (tobePicked.size() < numBooks) {
				randNum = rand.nextInt(rangePicks);
				tobePicked.add(randNum);
			}
		}

		// Return all the books by the randomly chosen indices.

		List<Book> ret =  tobePicked.stream()
				.map(index -> listAllEditorPicks.get(index).immutableBook())
				.collect(Collectors.toList());

		for (int isbn : bookMap.keySet()) {
			//release the lock after finish reading
			itemLocksMap.get(isbn).readLock().unlock();
		}

		listLock.readLock().unlock();
		return ret;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getTopRatedBooks(int)
	 */
	@Override
	public List<Book> getTopRatedBooks(int numBooks) throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooksInDemand()
	 */
	@Override
	public List<StockBook> getBooksInDemand() throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#rateBooks(java.util.Set)
	 */
	@Override
	public void rateBooks(Set<BookRating> bookRating) throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#removeAllBooks()
	 */
	public void removeAllBooks() throws BookStoreException {
		//lock the whole database
		listLock.writeLock().lock();
		bookMap.clear();
		itemLocksMap.clear();
		listLock.writeLock().unlock();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#removeBooks(java.util.Set)
	 */
	public void removeBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		//lock the whole database for deletion
		listLock.writeLock().lock();
		for (Integer ISBN : isbnSet) {
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
			}

			if (!bookMap.containsKey(ISBN)) {
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
			}
		}

		for (int isbn : isbnSet) {
			bookMap.remove(isbn);
		}
		listLock.writeLock().unlock();
	}
}
