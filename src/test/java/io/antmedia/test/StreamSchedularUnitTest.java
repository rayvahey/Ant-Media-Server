package io.antmedia.test;

import static org.bytedeco.javacpp.avcodec.av_packet_unref;
import static org.bytedeco.javacpp.avformat.av_read_frame;
import static org.bytedeco.javacpp.avformat.avformat_alloc_context;
import static org.bytedeco.javacpp.avformat.avformat_find_stream_info;
import static org.bytedeco.javacpp.avformat.avformat_open_input;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.bytedeco.javacpp.avcodec.AVPacket;
import org.bytedeco.javacpp.avformat;
import org.bytedeco.javacpp.avformat.AVFormatContext;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.avutil.AVDictionary;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.red5.server.scheduling.QuartzSchedulingService;
import org.red5.server.scope.WebScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.IDataStore;
import io.antmedia.datastore.db.MapDBStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.rest.model.Result;
import io.antmedia.streamsource.StreamFetcher;
import io.antmedia.streamsource.StreamFetcherManager;

@ContextConfiguration(locations = { "test.xml" })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class StreamSchedularUnitTest extends AbstractJUnit4SpringContextTests {

	public Application app = null;
	private WebScope appScope;
	protected static Logger logger = LoggerFactory.getLogger(StreamSchedularUnitTest.class);

	static {
		System.setProperty("red5.deployment.type", "junit");
		System.setProperty("red5.root", ".");


	}
	
	@Rule
	public TestRule watcher = new TestWatcher() {
	   protected void starting(Description description) {
	      System.out.println("Starting test: " + description.getMethodName());
	   }
	   
	   protected void failed(Throwable e, Description description) {
		   System.out.println("Failed test: " + description.getMethodName());
	   };
	   protected void finished(Description description) {
		   System.out.println("Finishing test: " + description.getMethodName());
	   };
	};
	private AntMediaApplicationAdapter appInstance;
	private QuartzSchedulingService scheduler;
	private AppSettings appSettings;

	@BeforeClass
	public static void beforeClass() {
		avformat.av_register_all();
		avformat.avformat_network_init();
	}

	@Before
	public void before() {
		File webApps = new File("webapps");
		if (!webApps.exists()) {
			webApps.mkdirs();
		}
		File junit = new File(webApps, "junit");
		if (!junit.exists()) {
			junit.mkdirs();
		}

		if (appScope == null) {
			appScope = (WebScope) applicationContext.getBean("web.scope");
			logger.debug("Application / web scope: {}", appScope);
			assertTrue(appScope.getDepth() == 1);
		}

		if (app == null) {
			app = (Application) applicationContext.getBean("web.handler");
			logger.debug("Application / web scope: {}", appScope);
			assertTrue(appScope.getDepth() == 1);
		}
		
		scheduler = (QuartzSchedulingService) applicationContext.getBean(QuartzSchedulingService.BEAN_NAME);

		//reset to default
		Application.enableSourceHealthUpdate = false;
		
	}

	@After
	public void after() {

		try {
			delete(new File("webapps"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//reset to default
		Application.enableSourceHealthUpdate = false;

	}

	public static void delete(File file) throws IOException {

		if (file.isDirectory()) {

			// directory is empty, then delete it
			if (file.list().length == 0) {

				file.delete();
				// System.out.println("Directory is deleted : "
				// + file.getAbsolutePath());

			} else {

				// list all the directory contents
				String files[] = file.list();

				for (String temp : files) {
					// construct the file structure
					File fileDelete = new File(file, temp);

					// recursive delete
					delete(fileDelete);
				}

				// check the directory again, if empty then delete it
				if (file.list().length == 0) {
					file.delete();
					// System.out.println("Directory is deleted : "
					// + file.getAbsolutePath());
				}
			}

		} else {
			// if file, then delete it
			file.delete();
			// System.out.println("File is deleted : " +
			// file.getAbsolutePath());
		}

	}

	/*
	 *@Test This test is commented out, because isStreamAlive is controlled instead of just controlling thread aliveness in {@link testThreadStopStart}
	 */
	public void testStreamSchedular() throws InterruptedException {

		try {
			AVFormatContext inputFormatContext = new AVFormatContext();

			Broadcast newCam = new Broadcast("testSchedular", "10.2.40.63:8080", "admin", "admin",
					"rtsp://184.72.239.149/vod/mp4:BigBuckBunny_115k.mov", "streamSource");

			StreamFetcher camScheduler = new StreamFetcher(newCam, appScope, null);

			camScheduler.setConnectionTimeout(10000);

			camScheduler.startStream();
			Thread.sleep(7000);

			//this should be false because this rtsp url cannot be used

			assertTrue(camScheduler.isStreamAlive());

			camScheduler.stopStream();

			Thread.sleep(5000);

			assertFalse(camScheduler.isStreamAlive());
			assertFalse(camScheduler.isThreadActive());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}

	}


	@Test
	public void testStreamSchedularConnectionTimeout() throws InterruptedException {
		logger.info("running testStreamSchedularConnectionTimeout");
		try {
			
			assertEquals(1, scheduler.getScheduledJobNames().size());
			

			AVFormatContext inputFormatContext = new AVFormatContext();

			Broadcast newCam = new Broadcast("testSchedular2", "10.2.40.64:8080", "admin", "admin",
					"rtsp://11.2.40.63:8554/live1.sdp", AntMediaApplicationAdapter.IP_CAMERA);

			newCam.setStreamId("new_cam" + (int)(Math.random()*10000));

			StreamFetcher streamScheduler = new StreamFetcher(newCam, appScope, null);

			assertFalse(streamScheduler.isExceptionInThread());

			streamScheduler.startStream();

			streamScheduler.setConnectionTimeout(3000);

			//this should be false because stream is not alive 
			assertFalse(streamScheduler.isStreamAlive());

			Thread.sleep(2500);

			streamScheduler.stopStream();

			Thread.sleep(3000);

			assertFalse(streamScheduler.isStreamAlive());

			assertFalse(streamScheduler.isExceptionInThread());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		logger.info("leaving testStreamSchedularConnectionTimeout");
		assertEquals(1, scheduler.getScheduledJobNames().size());
		
	}

	@Test
	public void testPrepareInput() throws InterruptedException {
		assertEquals(1, scheduler.getScheduledJobNames().size());
		
		try {

			Broadcast newCam = null;

			new StreamFetcher(newCam, appScope, null);

			fail("it should throw exception above");
		}
		catch (Exception e) {
		}

		try {
			AVFormatContext inputFormatContext = new AVFormatContext();

			Broadcast newCam2 = new Broadcast("test", "10.2.40.63:8080", "admin", "admin", null, AntMediaApplicationAdapter.IP_CAMERA);
			newCam2.setStreamId("newcam2_" + (int)(Math.random()*10000));

			new StreamFetcher(newCam2, appScope, null);

			fail("it should throw exception above");
		}
		catch (Exception e) {
		}
		assertEquals(1, scheduler.getScheduledJobNames().size());
		
	}
	
	
	@Test
	public void testAddCameraBug() {
		
		assertEquals(1, scheduler.getScheduledJobNames().size());
		
		boolean deleteHLSFilesOnExit = getAppSettings().isDeleteHLSFilesOnExit();
		
		getAppSettings().setDeleteHLSFilesOnEnded(false);
		
		
		Result result;
		IDataStore dataStore = new MapDBStore("target/testAddCamera.db"); //applicationContext.getBean(IDataStore.BEAN_NAME);

		assertNotNull(dataStore);
		StreamFetcherManager streamFetcherManager = new StreamFetcherManager(scheduler, dataStore, appScope);
		//app.setDataStore(dataStore);

		//set mapdb datastore to stream fetcher because in memory datastore just have references and updating broadcst
		// object updates the reference in inmemorydatastore
		//app.getStreamFetcherManager().setDatastore(dataStore);
		

		logger.info("running testAddCameraBug");
		Application.enableSourceHealthUpdate = true;
		assertNotNull(dataStore);
		
		startCameraEmulator();
		
		Broadcast newCam = new Broadcast("testAddCamera", "127.0.0.1:8080", "admin", "admin", "rtsp://127.0.0.1:6554/test.flv",
				AntMediaApplicationAdapter.IP_CAMERA);

		//add stream to data store
		dataStore.save(newCam);
		
		//result=getInstance().startStreaming(newCam);
		StreamFetcher streamFetcher = streamFetcherManager.startStreaming(newCam);
		
		//check whether answer from StreamFetcherManager is true or not after new IPCamera is added
		assertNotNull(streamFetcher);
		
		Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() ->  {
			return streamFetcher.isThreadActive();
		});
		
		//getInstance().stopStreaming(newCam);
		StreamFetcher streamFetcher2 = streamFetcherManager.stopStreaming(newCam);
		assertEquals(streamFetcher, streamFetcher2);
		stopCameraEmulator();
		
		streamFetcherManager.stopCheckerJob();
		
		Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() ->  {
			return !streamFetcher.isThreadActive();
		});
		
		Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() ->  {
			return 1 == scheduler.getScheduledJobNames().size();
		});
		
		getAppSettings().setDeleteHLSFilesOnEnded(deleteHLSFilesOnExit);
		Application.enableSourceHealthUpdate = false;
		
	}
	
	
	public void testIPTVStream() {

		AVFormatContext inputFormatContext = avformat_alloc_context();
		int ret;
		String url = "http://kaptaniptv.com:8000/live/oguzmermer2/jNwNLK1VLk/10476.ts";

		AVDictionary optionsDictionary = new AVDictionary();


		if ((ret = avformat_open_input(inputFormatContext, url, null, optionsDictionary)) < 0) {

			byte[] data = new byte[1024];
			avutil.av_strerror(ret, data, data.length);
			logger.error("cannot open input context with error: " + new String(data, 0, data.length) + "ret value = "+ String.valueOf(ret));
			return;
		}

		ret = avformat_find_stream_info(inputFormatContext, (AVDictionary) null);
		if (ret < 0) {
			logger.error("Could not find stream information\n");
			return;
		}

		AVPacket pkt = new AVPacket();

		long startTime = System.currentTimeMillis();

		int i = 0;
		while (true) {
			ret = av_read_frame(inputFormatContext, pkt);
			if (ret < 0) {
				byte[] data = new byte[1024];
				avutil.av_strerror(ret, data, data.length);

				logger.error("cannot read frame from input context: " + new String(data, 0, data.length));	
			}

			av_packet_unref(pkt);
			i++;
			if (i % 150 == 0) {
				long duration = System.currentTimeMillis() - startTime;

				logger.info("running duration: " + (duration/1000));
			}
		}
		/*
		long duration = System.currentTimeMillis() - startTime;

		logger.info("total duration: " + (duration/1000));
		avformat_close_input(inputFormatContext);
		inputFormatContext = null;

		 */

	}

	@Test
	public void testBandwidth() {
		
		
		startCameraEmulator();
		assertEquals(1, scheduler.getScheduledJobNames().size());
		
		boolean deleteHLSFilesOnExit = getAppSettings().isDeleteHLSFilesOnExit();
		getAppSettings().setDeleteHLSFilesOnEnded(false);
		
		File f = new File("target/test.db");
		if (f.exists()) {
			try {
				Files.delete(f.toPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		IDataStore dataStore = new MapDBStore("target/test.db"); //applicationContext.getBean(IDataStore.BEAN_NAME);

		assertNotNull(dataStore);
		app.setDataStore(dataStore);

		//set mapdb datastore to stream fetcher because in memory datastore just have references and updating broadcst
		// object updates the reference in inmemorydatastore
		app.getStreamFetcherManager().setDatastore(dataStore);
		

		logger.info("running testBandwidth");
		Application.enableSourceHealthUpdate = true;
		assertNotNull(dataStore);

		Broadcast newSource = new Broadcast("testBandwidth", "10.2.40.63:8080", "admin", "admin", "rtsp://127.0.0.1:6554/test.flv",
				AntMediaApplicationAdapter.STREAM_SOURCE);

		//add stream to data store
		dataStore.save(newSource);

		Broadcast newZombiSource = new Broadcast("testBandwidth", "10.2.40.63:8080", "admin", "admin", "rtsp://127.0.0.1:6554/test.flv",
				AntMediaApplicationAdapter.STREAM_SOURCE);

		newZombiSource.setZombi(true);
		//add second stream to datastore
		dataStore.save(newZombiSource);


		List<Broadcast> streams = new ArrayList<>();

		streams.add(newSource);
		streams.add(newZombiSource);

		//let stream fetching start
		app.getStreamFetcherManager().setStreamCheckerInterval(5000);
		app.getStreamFetcherManager().startStreams(streams);

		Awaitility.await().atMost(12, TimeUnit.SECONDS).until(() -> {
			return dataStore.get(newZombiSource.getStreamId()).getQuality() != null;
		});
		
		logger.info("before first control");

		List<Broadcast> broadcastList =  dataStore.getBroadcastList(0,  20);

		Broadcast fetchedBroadcast = null;

		for (Broadcast broadcast : broadcastList) {

			logger.info("broadcast name: " + broadcast.getName() + " broadcast status :" + broadcast.getStatus() + " broadcast is zombi: " + broadcast.isZombi());
			if(broadcast.isZombi()) {

				fetchedBroadcast=broadcast;	
				break;
			}
		}

		assertNotNull(fetchedBroadcast);
		assertEquals(fetchedBroadcast.getStreamId(), newZombiSource.getStreamId());
		assertNotNull(fetchedBroadcast.getQuality());
		assertNotNull(fetchedBroadcast.getSpeed());
		
		/*
		try {
			Thread.sleep(20000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		*/
		
		Awaitility.await().atMost(20, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
			Broadcast stream = dataStore.get(newSource.getStreamId());
			return stream != null && stream.getQuality() != null && stream.getQuality().equals("good");
		});
		
		Awaitility.await().atMost(20, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
			Broadcast stream = dataStore.get(newSource.getStreamId());
			logger.info("speed {}" , stream.getSpeed()) ;
			return stream != null && 1 < stream.getSpeed();
		});

		limitNetworkInterfaceBandwidth();

		/*
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		*/
		
		
		logger.info("Checking quality is again");
		Awaitility.await().atMost(5, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
			Broadcast streamTmp = dataStore.get(newSource.getStreamId());
			return streamTmp != null && streamTmp.getQuality() != null && !streamTmp.getQuality().equals("poor");
		});

		logger.info("before second control");

		assertNotEquals("poor", dataStore.get(newSource.getStreamId()).getQuality());
		
		logger.info("speed {}" , dataStore.get(newSource.getStreamId()).getSpeed()) ;

		resetNetworkInterface();

		for (Broadcast broadcast: broadcastList) {
			app.getStreamFetcherManager().stopStreaming(broadcast);
		}
		
		/*
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		*/
		Awaitility.await().atMost(5, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
			return app.getStreamFetcherManager().getStreamFetcherList().size() == 0;
		});

		//list size should be zero
		//assertEquals(0, app.getStreamFetcherManager().getStreamFetcherList().size());
		logger.info("leaving testBandwidth");
		
		Application.enableSourceHealthUpdate = false;
		
		getAppSettings().setDeleteHLSFilesOnEnded(deleteHLSFilesOnExit);
		
		assertEquals(1, scheduler.getScheduledJobNames().size());
		
		stopCameraEmulator();

	}
	
	private void runShellCommand(String[] params) {
		try {
			Process procStop = new ProcessBuilder(params).start();
			
			InputStream inputStream = procStop.getInputStream();
			byte[] data = new byte[1024];
			int length;
			while ((length = inputStream.read(data, 0, data.length)) > 0) {
				System.out.println(new String(data, 0, length));
			}
			
			inputStream = procStop.getErrorStream();
			while ((length = inputStream.read(data, 0, data.length)) > 0) {
				System.out.println(new String(data, 0, length));
			}
			
			procStop.waitFor();
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void resetNetworkInterface() {
		System.out.println("Running resetNetworkInterface");
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper clear wlan0" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper clear eth0" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper clear nic0" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper clear nic1" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper clear nic2" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper clear nic3" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper clear nic4" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper clear vmnet0" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper clear vmnet1" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper clear em1" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper clear em0" });
	}

	private void limitNetworkInterfaceBandwidth() {
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper wlan0 100 100" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper eth0 100 100" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper nic0 100 100" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper nic1 100 100" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper nic2 100 100" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper nic3 100 100" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper nic4 100 100" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper  vmnet0  100 100" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper  vmnet1 100  100" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper  em1  100  100" });
		
		runShellCommand(new String[] { "/bin/bash", "-c",
			"sudo wondershaper em0  100 100" });
		
	}

	public AntMediaApplicationAdapter getInstance() {
		if (appInstance == null) {
			appInstance = (AntMediaApplicationAdapter) applicationContext.getBean("web.handler");
		}
		return appInstance;
	}
	
	private void startCameraEmulator() {
		stopCameraEmulator();

		ProcessBuilder pb = new ProcessBuilder("/usr/local/onvif/runme.sh");
		Process p = null;
		try {
			p = pb.start();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


	private void stopCameraEmulator() {
		// close emulator in order to simulate cut-off
		String[] argsStop = new String[] { "/bin/bash", "-c",
		"kill -9 $(ps aux | grep 'onvifser' | awk '{print $2}')" };
		String[] argsStop2 = new String[] { "/bin/bash", "-c",
		"kill -9 $(ps aux | grep 'rtspserve' | awk '{print $2}')" };
		try {
			Process procStop = new ProcessBuilder(argsStop).start();
			Process procStop2 = new ProcessBuilder(argsStop2).start();


		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public AppSettings getAppSettings() {
		if (appSettings == null) {
			appSettings = (AppSettings) applicationContext.getBean(AppSettings.BEAN_NAME);
		}
		return appSettings;
	}



}


