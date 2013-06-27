package concrawler;

import java.io.BufferedInputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ParCrawler implements Crawler {
	private static final int MAX_RESULTS = 10;

	@Override
	public List<String> crawl(String startURL) {
		final ExecutorService exec = Executors.newFixedThreadPool(10);
		final CompletionService<List<String>> ecs = new ExecutorCompletionService<>(exec);
		
		final Queue<String> urlsToVisit = new LinkedList<>();
		final Set<String> urlsFound = new HashSet<>();
		
		urlsToVisit.add(startURL);
		
		while (!urlsToVisit.isEmpty() && urlsFound.size() < MAX_RESULTS) {
			for (String url : urlsToVisit) {
				ecs.submit(new CrawlHelper(url));
				urlsFound.add(url);
			}
			urlsToVisit.clear();
			
			try {
				List<String> res = ecs.take().get();
				for (String url : res) {
					addToVisit(urlsToVisit, urlsFound, url);
				}
				
				Future<List<String>> fut;
				while ((fut = ecs.poll()) != null) {
					for (String url : fut.get()) {
						addToVisit(urlsToVisit, urlsFound, url);
					}
				}
				
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		
		exec.shutdownNow();
		return new ArrayList<>(urlsFound);
	}

	private void addToVisit(final Queue<String> urlsToVisit,
			final Set<String> urlsFound, String url) {
		if (!urlsFound.contains(url) && !urlsToVisit.contains(url)) {
			urlsToVisit.add(url);
		}
	}
	
	private static class CrawlHelper implements Callable<List<String>> {
		private String startUrl;
		
		public CrawlHelper(String startUrl) {
			this.startUrl = startUrl;
		}
		
		@Override
		public List<String> call() throws Exception {
			URL	url = new URL(startUrl);	
			URLConnection conn = url.openConnection();	
			conn.setRequestProperty("User-­‐Agent", "ConCrawler/0.1 Mozilla/5.0");	
			
			List<String> result = new LinkedList<String>();	
			String contentType = conn.getContentType();	
			if (contentType != null && contentType.startsWith("text/html")) {
				BufferedInputStream is = null;	
				try {	
					is = new BufferedInputStream(conn.getInputStream());	
					Document doc = Jsoup.parse(is, null, startUrl);	
					Elements links = doc.select("a[href]");	
					for	(Element link : links) {
						String linkString = link.absUrl("href");	
						if (linkString.startsWith("http")) {
							result.add(linkString);	
						}	
					}
				} finally {	
					is.close();
				}	
			}	
			return result;	
		}
	}

}
