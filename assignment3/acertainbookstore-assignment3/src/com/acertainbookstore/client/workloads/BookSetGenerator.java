package com.acertainbookstore.client.workloads;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;

/**
 * Helper class to generate stockbooks and isbns modelled similar to Random
 * class
 */
public class BookSetGenerator {

	private Random random;
	private int initialBooks = 300;

	public BookSetGenerator() {
		// TODO Auto-generated constructor stub
		random = new Random();
	}

	/**
	 * Returns num randomly selected isbns from the input set
	 * 
	 * @param num
	 * @return
	 */
	public Set<Integer> sampleFromSetOfISBNs(Set<Integer> isbns, int num) {
		if (num < isbns.size())
			return isbns;
		Random rndm = this.random;
		HashSet<Integer> results = new HashSet<>();
		ArrayList<Integer> isbnsList = new ArrayList<>(isbns);
		for (int i = 0; i < num; i++) {
			int rnd = rndm.nextInt(isbnsList.size());
			Integer chosenIsbn = isbnsList.get(rnd);
			results.add(chosenIsbn);
			isbnsList.remove(rnd);
		}
		return results;
	}

	private String nextString(Random rnd, int length) {
		String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ ";
		StringBuilder salt = new StringBuilder();
//		Random rnd = new Random();
		while (salt.length() < length) { // length of the random string.
			int index = (int) (rnd.nextFloat() * SALTCHARS.length());
			salt.append(SALTCHARS.charAt(index));
		}
		String saltStr = salt.toString();
		return saltStr;
	}

	private float nextFloat(Random r, float min, float max) {
		float random = min + r.nextFloat() * (max - min);
		return random;
	}

	private int nextInt(Random r, int min, int max) {
		int random = min + r.nextInt(max-min);
		return random;
	}

	private long nextLong(Random r, int min, int max) {
		return (long)nextInt(r, min, max);
	}

	private boolean nextBool(Random r, float prob) {
		float p = r.nextFloat();
		return p > prob;
	}

	/**
	 * Return num stock books. For now return an ImmutableStockBook
	 * 
	 * @param num
	 * @return
	 */
	public Set<StockBook> nextSetOfStockBooks(int num) {
//		Random rndm = new Random();
//		HashSet<StockBook> results = new HashSet<>();
//		int[] set = IntStream.range(1, num * 3 + 1).toArray();
//		int index = set.length;
//		while (index > 1) {
//			final int pos = rndm.nextInt(index--);
//			final int tmp = set[index];
//			set[pos] = set[index];
//			set[index] = tmp;
//		}
//		for (int i = 0; i < num; i++) {
//			int isbn = set[i];
//			String title = nextString(rndm, 20);
//			String author = nextString(rndm, 10);
//			float price = nextFloat(rndm, 5.0f, 20.0f);
//			int numCopies = nextInt(rndm, 5, 70);
//			long numSaleMisses = nextLong(rndm, 1, 15);
//			long numTimesRated = nextLong(rndm, 5, 100);
//			long totalRating = nextLong(rndm, (int)numTimesRated, (int)numTimesRated * 5);
//			boolean editorPick = nextBool(rndm, 0.5f);
//
//			results.add(new ImmutableStockBook(isbn, title, author, price, numCopies, numSaleMisses, numTimesRated, totalRating, editorPick));
//		}
//
// 		return results;
		return nextSetOfStockBooksWithRange(num, initialBooks + 1, initialBooks + num * 3 + 1);
	}

	public Set<StockBook> nextSetOfStockBooksWithRange(int num, int min, int max) {
		Random rndm = this.random;
		HashSet<StockBook> results = new HashSet<>();
		int[] set = IntStream.range(min, max).toArray();
		int index = set.length;
		while (index > 1) {
			final int pos = rndm.nextInt(index--);
			final int tmp = set[index];
			set[pos] = set[index];
			set[index] = tmp;
		}
		for (int i = 0; i < num; i++) {
			int isbn = set[i];
			String title = nextString(rndm, 20);
			String author = nextString(rndm, 10);
			float price = nextFloat(rndm, 5.0f, 20.0f);
			int numCopies = nextInt(rndm, 5, 70);
			long numSaleMisses = nextLong(rndm, 1, 15);
			long numTimesRated = nextLong(rndm, 5, 100);
			long totalRating = nextLong(rndm, (int)numTimesRated, (int)numTimesRated * 5);
			boolean editorPick = nextBool(rndm, 0.5f);

			results.add(new ImmutableStockBook(isbn, title, author, price, numCopies, numSaleMisses, numTimesRated, totalRating, editorPick));
		}

		return results;
	}
}
