package edu.neu.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.attribute.PosixFilePermission;
import com.google.gson.Gson;

import edu.neu.comparators.*;
import edu.neu.dto.ForJsonObject;
import edu.neu.dto.URLMapper;

import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Map.Entry;

public class ServerCache {

	static String context = System.getProperty("user.dir");
	static long allFileSize = 0;
	static long maxLimit = 10485760;   //182,229 //1000000
 
	
	/**
	 * 
	 * @param args
	 * main arguments for the main method is a port number and the location of origin server.
	 * main method creates sockets binds to a port and accepts clients. checks if requests can be served from
	 * local cache or should be retrieved from origin server.
	 * NOTE: mapper file is a representation of MAP<httprequest,URLMapper> . and mapper is MAP<httprequest,URLMapper>
	 */
	public static void main(String[] args) {

		int port = Integer.parseInt(args[1]);
		String originServer = args[3];
		
		if(!originServer.contains("http://"))
		{
			originServer="http://"+originServer;
		}
		System.out.println("origin server string " + originServer);
		System.out.println("running on port :"+port);
		System.out.println("with origin server :"+originServer);
		URL url;
		InputStream is = null;
		BufferedReader bufRead;
		String Httpline = null;
		/** initialize map from file. map is used for all manipulation i.e.
		* searching using links.
		* maintains 10MB while continuous update removes not used files. This
		* happens only when there s a server miss.
		* the LRU value should be updated frequently.
		**/
		try {
			ServerSocket server = new ServerSocket(port);
			
			Map<String, URLMapper> mapper=new LinkedHashMap<String,URLMapper>();
			/** initialize the MAP from the files which was persisted else create a new file
			*The function returns an object of type ForJsonObject which has 
			*details on file locations, LRU count, Total file size.
			**/
			 
			ForJsonObject jsonWithMap=getMapFromFile();
			if(jsonWithMap==null)
			 mapper = mapper;
			else
			{
				mapper=jsonWithMap.getAllLinks();
				printMapContents(mapper);
			}
			boolean servedFromFile = false;
			boolean servedFromOrigin = false;
			System.out.println("===========================MAIN================================");
			System.out.println("Server Started . . . ");
			System.out.println("Files loaded from File size :" + allFileSize );
			while (true) {

				//accept connection from client and then process the request
				Socket socket = server.accept();
				BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				StringBuilder requestBuild = new StringBuilder();
				String line;
				//read all lines pushed from the client.
				while ((line = br.readLine()) != null) {
					requestBuild.append(line);
					if (!(line.length() > 0))
						break;
				}
				/**
				 * extract the get request from the parsed data. 
				 */
				if (requestBuild.length() > 0) {
					String httpRequest = null;
					String getRequest = requestBuild.toString();
					Pattern pat = Pattern.compile("GET .* HTTP");
					Matcher mat = pat.matcher(getRequest);
					if (mat.find()) {
						httpRequest = getRequest.substring(mat.start() + 4, mat.end() - 4).trim();
						// httpRequest=httpRequest.substring(4,
						// httpRequest.length()-4);
					}
					
					System.out.println("actual request from Client "+httpRequest);
					PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
					/**
					 * Check if the map is loaded and then check if the requested files are already in the cache.
					 * if available in cache return the files else check with origin by setting the boolean 
					 * servedFromFile to false
					 */
					if (mapper.size()>0) {
						
						System.out.println("does mapper has the requried key : "+mapper.containsKey(httpRequest));
						if (mapper.containsKey(httpRequest)) {
							Httpline = getHTMLFileFromStorage(mapper.get(httpRequest).getContentPath());
							if(Httpline!=null){
							mapper.get(httpRequest).setLruCount("0");
							mapper.get(httpRequest).setLruTime(new Date().getTime());
							servedFromFile = true;
							System.out.println("served from local cache");
							}
							else
							{
								mapper.remove(httpRequest);
								servedFromFile=false;
							}
						}

					}
					/**
					 * Get the file from the origin if available else send a 404 request.
					 * and continue for the next request.
					 * 
					 */
					if (!servedFromFile) {
						try {
							if (originServer.contains("8080"))
								Httpline = getHTMLFileFromOrigin(originServer + httpRequest);
							else
								Httpline = getHTMLFileFromOrigin(originServer + ":8080" + httpRequest);
							// writeToAFile(Httpline,httpRequest);
							servedFromOrigin = true;
							System.out.println("served from origin");
						} catch (Exception e) {
							e.printStackTrace();
							String str="The requested page is not available";
							out.println("HTTP/1.1 404 Not Found");
							out.println("Content-Length: " + str.length());
							out.println("Content-Type: text/html");
							out.println("Connection: Closed");
							out.print("\r\n\r\n");
							out.println(str);
							httpRequest="";
							servedFromFile=false;
							servedFromOrigin=false;
							continue;
						}
					}
					
					/**
					 * Send the generated http file to the client by setting 200 OK in headers.
					 */
					out.println("HTTP/1.1 200 OK");
					out.println("Content-Length: " + Httpline.length());
					out.println("Content-Type: text/html");
					out.println("Connection: Closed");
					out.print("\r\n\r\n");
					out.println(Httpline);
					/**
					 * if the file was taken from the local cache update the map and also the file which 
					 * is the persisted representation of the contents of the map.
					 * if the file was server from server add the file to the cache and update the mapper file.
					 */
					try{
					if (servedFromFile) {
						mapper=updateFileFromMap(httpRequest, mapper);
					} else if (servedFromOrigin) {
						mapper=addRecordAndHTMLPage(Httpline, httpRequest, mapper);
					}
					}
					/**
					 * any exception caught might reduce the consistency of mapper and cache but 
					 * keeps the server running.
					 */
					catch(Exception e)
					{
						continue;
					}
					/**
					 * server status attributes are reset for the next client.
					 */
					httpRequest="";
					servedFromFile=false;
					servedFromOrigin=false;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	/**
	 * 
	 * @param mapper
	 * prints the contents of the mapper. Used for debugging.
	 */
	private static void printMapContents(Map<String, URLMapper> mapper) {
		System.out.println("==============================printMapContents==========================");
		for (Map.Entry<String, URLMapper> entry : mapper.entrySet()) {
			System.out.println(entry.getKey() + " ==> " + entry.getValue().getContentPath() + " => " +entry.getValue().getLruCount()+" -> size of the file: "+entry.getValue().getSize());
			}
		System.out.println("================================================================");
	}
	/**
	 * 
	 * @param :httpline
	 * @param :httpRequest
	 * @param :mapper
	 * @return :Map which holds the details related to cache files. Basic file attributes with a LRU count.
	 * The function adds the records to the cache and performs replacement of files if necessary
	 * updates the mapper and the mapper file.  
	 */
	private static Map<String, URLMapper> addRecordAndHTMLPage(String httpline, String httpRequest,
			Map<String, URLMapper> mapper) {
		System.out.println("==============================addRecordAndHTMLPage==========================");
		long sizeOfFile = (long) httpline.length();
		long allFileSizeTemp=allFileSize;
		System.out.println("Function Name addRecordAndHTMLPage : variable httpRequest :"+httpRequest);
		String newPath = httpRequest.substring(1).replaceAll("/", "-");
		newPath=newPath.trim();
		URLMapper newFileMap = new URLMapper();
		newFileMap.setContentPath(newPath);
		System.out.println("Size of File " + sizeOfFile);
		newFileMap.setSize(sizeOfFile);
		newFileMap.setLruCount("0");
		newFileMap.setLruTime(new Date().getTime());
		
		allFileSizeTemp = allFileSize + sizeOfFile;
		/**
		 * checks for maxlimit and performs LRU operation.
		 */
		if(allFileSizeTemp>maxLimit)
		{
			mapper=performRemovalOfFiles(mapper,sizeOfFile);
		}
		/**
		 * state of the mapper is updated and the mapper file is updated
		 */
		mapper.put(httpRequest, newFileMap);
		mapper=updateFileFromMap(httpRequest,mapper);
		allFileSize=allFileSize+sizeOfFile;
		
		try {
			/**
			 * persisting the obtained html file from the origin.
			 */
			File file = new File(context+"//HTMLFiles//"+newPath + ".html");
			FileWriter writer;
			try {
				writer = new FileWriter(file, false);
				PrintWriter printer = new PrintWriter(writer);
				printer.write(httpline);

				printer.close();
				writer.close();
				return mapper;
			} catch (Exception e) {
				e.printStackTrace();

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return mapper;
	}
	/**
	 * 
	 * @param mapper
	 * @param sizeOfFile
	 * @return mapper
	 * This function removes files which are old and not used for a longer time.
	 * checks if the incoming file can be satisfied by removing a single file if not multiple files are
	 * removed. Once removed mapper is updated and current file size is updated. 
	 */
	private static Map<String, URLMapper> performRemovalOfFiles(Map<String, URLMapper> mapper,long sizeOfFile) {
		
		System.out.println("===============================performRemovalOfFiles================================");
		List<String> removeKeyList=new ArrayList<String>();
		//System.out.println("Inside Perform Removal Of Files");
		Set<Entry<String, URLMapper>> entries = mapper.entrySet();
		List<Entry<String, URLMapper>> listOfEntries = new ArrayList<Entry<String, URLMapper>>(entries);
		if(mapper.size()>1){
			
			long size=0;
			for(int i=mapper.size()-1;i>0;i--)
			{	
				Entry enterLast= listOfEntries.get(i);  
				URLMapper urlMapper=(URLMapper) enterLast.getValue();
				size=size+urlMapper.getSize();
				if(sizeOfFile<=size)
				{
					removeKeyList.add((String)enterLast.getKey());
					break;
				}
				else
				{
					removeKeyList.add((String)enterLast.getKey());
				}
			}
			long totSizeRemoved=0;
			for(String removeElem:removeKeyList)
			{
				URLMapper urlMapper=mapper.remove(removeElem);
				totSizeRemoved+=urlMapper.getSize();
				removeOldFiles(urlMapper.getContentPath());
			}
			System.out.println("The size of the incoming file "+sizeOfFile + " the size that is freed "+ totSizeRemoved);
			
			allFileSize=allFileSize-totSizeRemoved;
			
			printMapContents(mapper);
		System.out.println("===============================================================");
		}
		return mapper;
	}
	/**
	 * 
	 * @param contentPath
	 * removes the file specified in the argument.
	 */
	private static void removeOldFiles(String contentPath) {
		System.out.println("==============================removeOldFiles=================================");
		//System.out.println("Inside Remove Old Files");
		String fileName = context + "//HTMLFiles//"+contentPath+".html";
		File file = new File(fileName);
		boolean fileDeleted= file.delete();
		System.out.println("file deleted "+fileName + " deletion message "+fileDeleted);
		
		System.out.println("==============================================================================");
		
	}
	/**
	 * 
	 * @param httpRequest
	 * @param mapper
	 * @return Mapper
	 * mapper file is updated using the mapper the mapper is converted to a JSON and then stored in a file
	 */
	private static Map<String, URLMapper> updateFileFromMap(String httpRequest, Map<String, URLMapper> mapper) {

		System.out.println("==============================updateFileFromMap=================================");
		long allFileSize = (long) 0;
		
		for (Map.Entry<String, URLMapper> entry : mapper.entrySet()) {
			Integer lruCount=0;
			if (!entry.getKey().equalsIgnoreCase(httpRequest)) {
				lruCount= Integer.parseInt((entry.getValue().getLruCount())) + 1;
			}
			
				entry.getValue().setLruCount(lruCount.toString());
				allFileSize += entry.getValue().getSize();
			
		}
		mapper = checkMapSorting(mapper);
		printMapContents(mapper);
		
		String fileName = context + "//mapper//DataMapper.txt";

		ForJsonObject mapToJson = new ForJsonObject();
		mapToJson.setAllLinks(mapper);
		mapToJson.setFullSize(allFileSize);

		Gson gson = new Gson();
		String json = gson.toJson(mapToJson);

		try {

			File file = new File(fileName);
			FileWriter writer;
			writer = new FileWriter(file, false);
			PrintWriter printer = new PrintWriter(writer);
			printer.write(json);
			printer.close();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return mapper;
	}
	/**
	 * 
	 * @param contentPath
	 * @return
	 * Picks the http files present in the cache and returns the same.
	 */
	private static String getHTMLFileFromStorage(String contentPath) {
		
		System.out.println("===========================getHTMLFileFromStorage================================");
		String fileName = context +"//HTMLFiles//"+contentPath+".html";
		StringBuilder strBuild = new StringBuilder();
		try {
			File text = new File(fileName);
			if(text.exists()){
			Scanner scanner = new Scanner(text);
			while (scanner.hasNext()) {
				strBuild.append(scanner.next());
			}
			scanner.close();
			}
			else
			{
				return null;
			}
			return strBuild.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}
	/**
	 * 
	 * @return a object which is used for JSON representation.
	 * the method gets the mapper contains from the file which is persisted . Mainly Used while bootstrapping
	 */
	private static ForJsonObject getMapFromFile() {
		
		System.out.println("=================================getMapFromFile===================================");
		String fileName = context + "//mapper//DataMapper.txt";
		StringBuilder strBuild = new StringBuilder();
		Gson gson = new Gson();
		try {
			File text = new File(fileName);
			Files.setPosixFilePermissions(text.toPath(), 
				    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, 
				    		PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE));
			
			if (text.createNewFile()){
		        System.out.println("File is created!");
		      }else{
		        System.out.println("File already exists.");
		      }
			
			
			Scanner scanner = new Scanner(text);
			while (scanner.hasNext()) {
				strBuild.append(scanner.next());
			}
			scanner.close();
			
			ForJsonObject response = gson.fromJson(strBuild.toString(), ForJsonObject.class);
			if(response!=null)
			allFileSize = response.getFullSize();
			else
				allFileSize=0;
			return response;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}
	/**
	 * 
	 * @param urlToRead
	 * @return httpFile 
	 * @throws Exception
	 * gets the http request from the origin and returns the same if the file doesnt exist error is thrown
	 */
	public static String getHTMLFileFromOrigin(String urlToRead) throws Exception {
		try{
		System.out.println("============================getHTMLFileFromOrigin======================================");	
		StringBuilder result = new StringBuilder();
		URL url = new URL(urlToRead);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		String line;
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}
		rd.close();
		conn.disconnect();
		return result.toString();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw e;
		}
	}
	/**
	 * 
	 * @param mapper
	 * @return mapper which is sorted
	 * sort the map in ascending order based on the LRU count and also on the creation time.
	 */
	public static Map checkMapSorting(Map<String, URLMapper> mapper) {

		Set<Entry<String, URLMapper>> entries = mapper.entrySet();
		List<Entry<String, URLMapper>> listOfEntries = new ArrayList<Entry<String, URLMapper>>(entries);

		Collections.sort(listOfEntries, new LRUComparator());

		LinkedHashMap<String, URLMapper> sortedByLRU = new LinkedHashMap<String, URLMapper>(listOfEntries.size());

		for (Entry<String, URLMapper> entry : listOfEntries) {
			sortedByLRU.put(entry.getKey(), entry.getValue());
		}
		Set<Entry<String, URLMapper>> entrySetSortedByValue = sortedByLRU.entrySet();
		for (Entry<String, URLMapper> mapping : entrySetSortedByValue) {
			System.out.println(mapping.getKey() + " ==> " + mapping.getValue().getContentPath() + " => "
					+ mapping.getValue().getLruCount());
		}

		return sortedByLRU;

	}
	
	

}
