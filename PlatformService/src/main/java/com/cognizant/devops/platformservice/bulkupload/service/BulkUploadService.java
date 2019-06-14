
/*******************************************************************************
 * Copyright 2017 Cognizant Technology Solutions
 *   
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * 	of the License at
 *   
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *   
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package com.cognizant.devops.platformservice.bulkupload.service;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.multipart.MultipartFile;

import com.cognizant.devops.platformcommons.dal.neo4j.GraphDBException;
import com.cognizant.devops.platformcommons.dal.neo4j.GraphResponse;
import com.cognizant.devops.platformcommons.dal.neo4j.Neo4jDBHandler;
import com.cognizant.devops.platformcommons.exception.InsightsCustomException;
import com.cognizant.devops.platformservice.rest.datatagging.constants.DatataggingConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
public class BulkUploadService {

private static final BulkUploadService bulkUploadService = new BulkUploadService();
	private static final Logger log = LogManager.getLogger(BulkUploadService.class);

	private BulkUploadService() {

	}

	public static BulkUploadService getInstance() {
		return bulkUploadService;
	}

	public boolean createBulkUploadMetaData(MultipartFile file,String toolName) throws InsightsCustomException {

		File csvfile = null;
		boolean status = false;
		try {
			csvfile = convertToFile(file);
		} catch (IOException ex) {
			log.debug("Exception while creating csv on server", ex);
			return status;
		}
		CSVFormat format = CSVFormat.newFormat(',').withHeader();
		try (Reader reader = new FileReader(csvfile); CSVParser csvParser = new CSVParser(reader, format);) {

			Neo4jDBHandler dbHandler = new Neo4jDBHandler();
			Map<String, Integer> headerMap = csvParser.getHeaderMap();
			dbHandler.executeCypherQuery("CREATE CONSTRAINT ON (n:METADATA) ASSERT n.metadata_id  IS UNIQUE");
			String query = "UNWIND {props} AS properties " + "CREATE (n:METADATA:BULKUPLOAD2) " + "SET n = properties";
			status = parseCsvRecords(status, csvParser, dbHandler, headerMap, query);

		} catch (FileNotFoundException e) {
			log.error("File not found Exception in uploading csv file", e);
			throw new InsightsCustomException("File not found Exception in uploading csv file");
		} catch (IOException | GraphDBException e) {
			log.error("IOException in uploading csv file", e);
			throw new InsightsCustomException("IOException in uploading csv file");
		} catch (InsightsCustomException e) {
			log.error("Duplicate record in CSV file", e);
			throw new InsightsCustomException("Duplicate record in CSV file");
		}
		return status;

	}

	private boolean parseCsvRecords(boolean status, CSVParser csvParser, Neo4jDBHandler dbHandler,
			Map<String, Integer> headerMap, String query)
			throws IOException, GraphDBException, InsightsCustomException {
		List<JsonObject> nodeProperties = new ArrayList<>();
		
		
		int record = 0;
		for (CSVRecord csvRecord : csvParser.getRecords()) {
						
			
			JsonObject json = getHierachyDetails(csvRecord, headerMap);
			record = record + 1;
			json.addProperty(DatataggingConstants.METADATA_ID, Instant.now().getNano() + record);
			json.addProperty(DatataggingConstants.CREATIONDATE, Instant.now().toEpochMilli());
			nodeProperties.add(json);
			
		}
		JsonObject graphResponse = dbHandler.bulkCreateNodes(nodeProperties, null, query);
		if (graphResponse.get(DatataggingConstants.RESPONSE).getAsJsonObject().get(DatataggingConstants.ERRORS)
				.getAsJsonArray().size() > 0) {
			log.error(graphResponse);
			return status;
		}

		return true;
	}



	
	

	private JsonObject getHierachyDetails(CSVRecord record, Map<String, Integer> headerMap) {
		JsonObject json = new JsonObject();
		for (Map.Entry<String, Integer> header : headerMap.entrySet()) {
			if (header.getKey() != null && !DatataggingConstants.ACTION.equalsIgnoreCase(header.getKey())) {
				if (DatataggingConstants.METADATA_ID.equalsIgnoreCase(header.getKey())
						&& (record.get(header.getValue()) != null && !record.get(header.getValue()).isEmpty())) {
					json.addProperty(header.getKey(), Integer.valueOf(record.get(header.getValue())));
				} else {
					json.addProperty(header.getKey(), record.get(header.getValue()));
				}
			}
		}
		return json;
	}

	

	

	
	

	private File convertToFile(MultipartFile multipartFile) throws IOException {
		File file = new File(multipartFile.getOriginalFilename());

		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(multipartFile.getBytes());
		}

		return file;
	}

}