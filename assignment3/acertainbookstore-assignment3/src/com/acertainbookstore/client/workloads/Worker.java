/**
 * 
 */
package com.acertainbookstore.client.workloads;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.BookStoreBook;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreException;

/**
 * 
 * Worker represents the workload runner which runs the workloads with
 * parameters using WorkloadConfiguration and then reports the results
 * 
 */
public class Worker implements Callable<WorkerRunResult> {
    private WorkloadConfiguration configuration = null;
    private int numSuccessfulFrequentBookStoreInteraction = 0;
    private int numTotalFrequentBookStoreInteraction = 0;

    public Worker(WorkloadConfiguration config) {
	configuration = config;
    }

    /**
     * Run the appropriate interaction while trying to maintain the configured
     * distributions
     * 
     * Updates the counts of total runs and successful runs for customer
     * interaction
     * 
     * @param chooseInteraction
     * @return
     */
    private boolean runInteraction(float chooseInteraction) {
	try {
	    float percentRareStockManagerInteraction = configuration.getPercentRareStockManagerInteraction();
	    float percentFrequentStockManagerInteraction = configuration.getPercentFrequentStockManagerInteraction();

	    if (chooseInteraction < percentRareStockManagerInteraction) {
		runRareStockManagerInteraction();
	    } else if (chooseInteraction < percentRareStockManagerInteraction
		    + percentFrequentStockManagerInteraction) {
		runFrequentStockManagerInteraction();
	    } else {
		numTotalFrequentBookStoreInteraction++;
		runFrequentBookStoreInteraction();
		numSuccessfulFrequentBookStoreInteraction++;
	    }
	} catch (BookStoreException ex) {
	    return false;
	}
	return true;
    }

    /**
     * Run the workloads trying to respect the distributions of the interactions
     * and return result in the end
     */
    public WorkerRunResult call() throws Exception {
		int count = 1;
		long startTimeInNanoSecs = 0;
		long endTimeInNanoSecs = 0;
		int successfulInteractions = 0;
		long timeForRunsInNanoSecs = 0;

		Random rand = new Random();
		float chooseInteraction;

		// Perform the warmup runs
		while (count++ <= configuration.getWarmUpRuns()) {
			chooseInteraction = rand.nextFloat() * 100f;
			runInteraction(chooseInteraction);
		}

		count = 1;
		numTotalFrequentBookStoreInteraction = 0;
		numSuccessfulFrequentBookStoreInteraction = 0;

		// Perform the actual runs
		startTimeInNanoSecs = System.nanoTime();
		while (count++ <= configuration.getNumActualRuns()) {
			chooseInteraction = rand.nextFloat() * 100f;
			if (runInteraction(chooseInteraction)) {
			successfulInteractions++;
			}
		}
		endTimeInNanoSecs = System.nanoTime();
		timeForRunsInNanoSecs += (endTimeInNanoSecs - startTimeInNanoSecs);
		return new WorkerRunResult(successfulInteractions, timeForRunsInNanoSecs, configuration.getNumActualRuns(),
			numSuccessfulFrequentBookStoreInteraction, numTotalFrequentBookStoreInteraction);
    }

    /**
     * Runs the new stock acquisition interaction
     * 
     * @throws BookStoreException
     */
    private void runRareStockManagerInteraction() throws BookStoreException {
	// TODO: Add code for New Stock Acquisition Interaction
//		BookStore bookStore = this.configuration.getBookStore();
		StockManager stockManager = this.configuration.getStockManager();

		Set<Integer> allISBNs = stockManager.getBooks()
				.stream()
				.map(b -> b.getISBN())
				.collect(Collectors.toSet());
		Set<StockBook> booksToAdd = this.configuration.getBookSetGenerator()
				.nextSetOfStockBooks(this.configuration.getNumBooksToAdd())
				.stream().filter(b -> !allISBNs.contains(b.getISBN()))
				.collect(Collectors.toSet());
		stockManager.addBooks(booksToAdd);
    }

    /**
     * Runs the stock replenishment interaction
     * 
     * @throws BookStoreException
     */
    private void runFrequentStockManagerInteraction() throws BookStoreException {
	// TODO: Add code for Stock Replenishment Interaction
//		BookStore bookStore = this.configuration.getBookStore();
		StockManager stockManager = this.configuration.getStockManager();
		List<StockBook> allBooks = stockManager.getBooks();
		Comparator<StockBook> comparator = Comparator.comparing(StockBook::getNumCopies);
		allBooks = allBooks.stream().sorted(comparator)
				.collect(Collectors.toList())
				.subList(0, Math.min(allBooks.size(), this.configuration.getNumBooksWithLeastCopies()));
		Set<BookCopy> booksToAdd = allBooks.stream()
				.map(p -> new BookCopy(p.getISBN(), this.configuration.getNumAddCopies()))
				.collect(Collectors.toSet());
		stockManager.addCopies(booksToAdd);
    }

    /**
     * Runs the customer interaction
     * 
     * @throws BookStoreException
     */
    private void runFrequentBookStoreInteraction() throws BookStoreException {
	// TODO: Add code for Customer Interaction
		BookStore bookStore = this.configuration.getBookStore();
//		StockManager stockManager = this.configuration.getStockManager();
		BookSetGenerator bookSetGenerator = this.configuration.getBookSetGenerator();

		Set<Integer> editorPicksISBNs = bookStore
				.getEditorPicks(this.configuration.getNumEditorPicksToGet())
				.stream()
				.map(b -> b.getISBN())
				.collect(Collectors.toSet());
		Set<BookCopy> booksToBuy = bookSetGenerator
				.sampleFromSetOfISBNs(editorPicksISBNs, this.configuration.getNumBooksToBuy())
				.stream().map(b -> new BookCopy(b, this.configuration.getNumBookCopiesToBuy()))
				.collect(Collectors.toSet());
		bookStore.buyBooks(booksToBuy);
    }

}
