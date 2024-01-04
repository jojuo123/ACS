/**
 * 
 */
package com.acertainbookstore.client.workloads;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.acertainbookstore.business.CertainBookStore;
import com.acertainbookstore.client.BookStoreClientConstants;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.server.BookStoreHTTPServer;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * 
 * CertainWorkload class runs the workloads by different workers concurrently.
 * It configures the environment for the workers using WorkloadConfiguration
 * objects and reports the metrics
 * 
 */
public class CertainWorkload {

	public static List<String[]> dataLines;
	private static String local = "";

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		dataLines = new ArrayList<>();
		int numConcurrentWorkloadThreads = 100;
		String serverAddress = "http://localhost:8081";
		boolean localTest = false;
		local = localTest ? "local" : "RPC";
		List<WorkerRunResult> workerRunResults = new ArrayList<WorkerRunResult>();
		List<Future<WorkerRunResult>> runResults = new ArrayList<Future<WorkerRunResult>>();

		// Initialize the RPC interfaces if its not a localTest, the variable is
		// overriden if the property is set
		String localTestProperty = System
				.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
		localTest = (localTestProperty != null) ? Boolean
				.parseBoolean(localTestProperty) : localTest;

		BookStore bookStore = null;
		StockManager stockManager = null;
		if (localTest) {
			CertainBookStore store = new CertainBookStore();
			bookStore = store;
			stockManager = store;
		} else {
			stockManager = new StockManagerHTTPProxy(serverAddress + "/stock");
			bookStore = new BookStoreHTTPProxy(serverAddress);
		}

		// Generate data in the bookstore before running the workload
		initializeBookStoreData(bookStore, stockManager);

		ExecutorService exec = Executors
				.newFixedThreadPool(numConcurrentWorkloadThreads);

		for (int i = 0; i < numConcurrentWorkloadThreads; i++) {
			WorkloadConfiguration config = new WorkloadConfiguration(bookStore,
					stockManager);
			Worker workerTask = new Worker(config);
			// Keep the futures to wait for the result from the thread
			runResults.add(exec.submit(workerTask));
		}

		// Get the results from the threads using the futures returned
		for (Future<WorkerRunResult> futureRunResult : runResults) {
			WorkerRunResult runResult = futureRunResult.get(); // blocking call
			workerRunResults.add(runResult);
		}

		exec.shutdownNow(); // shutdown the executor

		// Finished initialization, stop the clients if not localTest
		if (!localTest) {
			((BookStoreHTTPProxy) bookStore).stop();
			((StockManagerHTTPProxy) stockManager).stop();
		}

		reportMetric(workerRunResults);
	}

	/**
	 * Computes the metrics and prints them
	 * 
	 * @param workerRunResults
	 */
	public static void reportMetric(List<WorkerRunResult> workerRunResults) {
		// TODO: You should aggregate metrics and output them for plotting here
		int totalInteractions = 0;
		int successfulInteractions = 0;
		int totalRuns = 0;
		float latency = 0;
		float throughput = 0.0f;
		float goodput = 0.0f;
		int fail_goodput = 0;
		int fail_customer_rate = 0;

		for (WorkerRunResult wrr : workerRunResults) {
			totalInteractions += wrr.getTotalFrequentBookStoreInteractionRuns();
			totalRuns += wrr.getTotalRuns();
			successfulInteractions += wrr.getSuccessfulFrequentBookStoreInteractionRuns();

			goodput += wrr.getSuccessfulFrequentBookStoreInteractionRuns() * 1.0f / (wrr.getElapsedTimeInNanoSecs() / 1e6);
			throughput += wrr.getTotalFrequentBookStoreInteractionRuns() * 1.0f / (wrr.getElapsedTimeInNanoSecs() / 1e6);

			latency += wrr.getElapsedTimeInNanoSecs() * 1.0f / 1e6;

		}

		float failRatio = 1.0f - goodput / throughput;
		if (failRatio > 0.01) {
			System.out.println("goodput and throughput are not closed enough: " + String.valueOf(failRatio));
			fail_goodput = 1;
		}

		float customerRatio = 1.0f * totalInteractions / totalRuns;
		if (!(customerRatio > 0.55 && customerRatio < 0.65)) {
			System.out.println("Customer Interactions is " + String.valueOf(customerRatio * 100) + "%");
			fail_customer_rate = 1;
		}

		float avgLatency = latency / successfulInteractions;
		System.out.println("result: " + throughput + ", " + avgLatency);

		String is_binary = BookStoreConstants.BINARY_SERIALIZATION ? "1" : "0";
		int client_max_threadpool_threads = BookStoreClientConstants.CLIENT_MAX_THREADSPOOL_THREADS;
		int min_server_threadpool = BookStoreHTTPServer.getMinThreadpoolSize();
		int max_server_threadpool = BookStoreHTTPServer.getMaxThreadpoolSize();

		String[] dataline = new String[]
				{String.valueOf(workerRunResults.size()), String.valueOf(throughput), String.valueOf(avgLatency), String.valueOf(fail_goodput), String.valueOf(fail_customer_rate), is_binary, String.valueOf(client_max_threadpool_threads), String.valueOf(min_server_threadpool), String.valueOf(max_server_threadpool), local, "computer 1"};
		String line = Stream.of(dataline).collect(Collectors.joining(","));

		//Number of clients,throughput,latency,fail goodput,fail customer rate,binary serialization,client max threadpool threads,server min threadpool size,server max threadpool size,address space,machine
		try {
			FileWriter fw = new FileWriter("./result.csv", true);
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(line);
			System.out.println(line);
			bw.newLine();
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Generate the data in bookstore before the workload interactions are run
	 * 
	 * Ignores the serverAddress if its a localTest
	 * 
	 */
	public static void initializeBookStoreData(BookStore bookStore,
			StockManager stockManager) throws BookStoreException {
		stockManager.removeAllBooks();

		// TODO: You should initialize data for your bookstore here
//		Random random = new Random();
		int n = 250;
		stockManager.addBooks(new BookSetGenerator().nextSetOfStockBooksWithRange(n, 1, n+1));
	}
}
