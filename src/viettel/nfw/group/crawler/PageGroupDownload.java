/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package viettel.nfw.group.crawler;

import java.io.FileNotFoundException;
import java.io.IOException;
import javax.management.timer.Timer;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.RootLogger;
import org.json.simple.parser.ParseException;
import viettel.nfw.page.crawler.DownloaderPage;

/**
 *
 * @author hoangvv
 */
public class PageGroupDownload {

	private static final Logger logger = RootLogger.getLogger(PageGroupDownload.class);

	/**
	 * @param args the command line arguments
	 * @throws java.io.IOException
	 */
	static {
		try {
			PropertyConfigurator.configure("etc/log4j.cfg");
		} catch (Exception e) {
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException, FileNotFoundException, ParseException {
		Thread downloadPage = new Thread(new DownloaderPage());
		downloadPage.start();
		Thread downloadGroup = new Thread(new DownloaderGroup());
		downloadGroup.start();
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						Thread.sleep(5 * Timer.ONE_MINUTE);
						logger.info("Facebook's Group crawl speed: " + (DownloaderGroup.count.getAndSet(0) / 5) + " profiles/minute");
						logger.info("Facebook's Page crawl speed: " + (DownloaderPage.count.getAndSet(0) / 5) + " profiles/minute");
					} catch (InterruptedException ex) {
						logger.error(ex.getMessage(), ex);
					}
				}
			}
		});
		t.start();

	}
}
