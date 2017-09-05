/*******************************************************************************
 * Copyright 2017 Cognizant Technology Solutions
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.cognizant.devops.platformservice.rest.AccessGroupManagement;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.NewCookie;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cognizant.devops.platformcommons.config.ApplicationConfigProvider;
import com.cognizant.devops.platformcommons.config.GrafanaData;
import com.cognizant.devops.platformcommons.dal.rest.RestHandler;
import com.cognizant.devops.platformservice.rest.util.PlatformServiceUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;

@RestController
@RequestMapping("/accessGrpMgmt")
public class AccessGroupManagement {
	private static Logger log = Logger.getLogger(AccessGroupManagement.class.getName());
	private static String authHeader = null;
	
	@Autowired
	private HttpServletRequest httpRequest;
	
	@RequestMapping(value = "/getOrgs", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
	public JsonObject getOrgs(){
		String apiUrl = ApplicationConfigProvider.getInstance().getGrafana().getGrafanaEndpoint()+"/api/orgs";
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", buildAuthenticationHeader());
		ClientResponse response = RestHandler.doGet(apiUrl, null, headers);
		return PlatformServiceUtil.buildSuccessResponseWithData(new JsonParser().parse(response.getEntity(String.class)));
	}
	
	@RequestMapping(value = "/switchUserOrg", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
	public JsonObject switchUserOrg(@RequestParam int orgId){
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Cookie", getUserCookies());
		String apiUrl = ApplicationConfigProvider.getInstance().getGrafana().getGrafanaEndpoint()+"/api/user/using/"+orgId;
		ClientResponse response = RestHandler.doPost(apiUrl, null, headers);
		return PlatformServiceUtil.buildSuccessResponseWithData(new JsonParser().parse(response.getEntity(String.class)));
	}
	
	@RequestMapping(value = "/getCurrentUserOrgs", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
	public JsonObject getCurrentUserOrgs(){
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Cookie", getUserCookies());
		String apiUrl = ApplicationConfigProvider.getInstance().getGrafana().getGrafanaEndpoint()+"/api/user/orgs";
		ClientResponse response = RestHandler.doGet(apiUrl, null, headers);
		return PlatformServiceUtil.buildSuccessResponseWithData(new JsonParser().parse(response.getEntity(String.class)));
	}
	
	@RequestMapping(value = "/createOrg", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
	public String createOrg(@RequestParam String orgName){
		String apiUrl = ApplicationConfigProvider.getInstance().getGrafana().getGrafanaEndpoint()+"/api/orgs";
		JsonObject request = new JsonObject();
		request.addProperty("name", orgName);
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", buildAuthenticationHeader());
		ClientResponse response = RestHandler.doPost(apiUrl, request, headers);
		return response.getEntity(String.class);
	}
	
	@RequestMapping(value = "/getUser", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
	public JsonObject getUser(){
		String apiUrl = ApplicationConfigProvider.getInstance().getGrafana().getGrafanaEndpoint()+"/api/user";
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Cookie", getUserCookies());
		ClientResponse response = RestHandler.doGet(apiUrl, null, headers);
		return PlatformServiceUtil.buildSuccessResponseWithData(new JsonParser().parse(response.getEntity(String.class)));
	}

	private String getUserCookies(){
		Cookie[] cookies = httpRequest.getCookies();
		StringBuffer grafanaCookie = new StringBuffer();
		if(cookies != null){
			for(Cookie cookie : cookies){
				grafanaCookie.append(cookie.getName()).append("=").append(cookie.getValue()).append(";");
			}
		}else{
			try {
				String authHeader = httpRequest.getHeader("Authorization");
				String decodedAuthHeader = new String(Base64.getDecoder().decode(authHeader.split(" ")[1]), "UTF-8");
				String[] authTokens = decodedAuthHeader.split(":");
				JsonObject loginRequestParams = new JsonObject();
				loginRequestParams.addProperty("user", authTokens[0]);
				loginRequestParams.addProperty("password", authTokens[1]);
				String loginApiUrl = ApplicationConfigProvider.getInstance().getGrafana().getGrafanaEndpoint()+"/login";
				ClientResponse grafanaLoginResponse = RestHandler.doPost(loginApiUrl, loginRequestParams, null);
				List<NewCookie> cookies2 = grafanaLoginResponse.getCookies();
				for(NewCookie cookie : cookies2){
					grafanaCookie.append(cookie.getName()).append("=").append(cookie.getValue()).append(";");
				}
			} catch (UnsupportedEncodingException e) {
				log.error("Unable to get grafana session.", e);
			}
			/*Object attribute = httpRequest.getAttribute("responseHeaders");
			if(attribute != null){
				Map<String, String> responseHeaders = (Map)attribute;
				for(Map.Entry<String, String> entry : responseHeaders.entrySet()){
					grafanaCookie.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
				}
			}*/
		}
		
		return grafanaCookie.toString();
	}
	
	private List<NewCookie> getValidGrafanaSession(String userName, String password) {
		JsonObject loginRequestParams = new JsonObject();
		loginRequestParams.addProperty("user", userName);
		loginRequestParams.addProperty("password", password);
		String loginApiUrl = ApplicationConfigProvider.getInstance().getGrafana().getGrafanaEndpoint()+"/login";
		ClientResponse grafanaLoginResponse = RestHandler.doPost(loginApiUrl, loginRequestParams, null);
		return grafanaLoginResponse.getCookies();
	}
	
	private String buildAuthenticationHeader(){
		GrafanaData grafana = ApplicationConfigProvider.getInstance().getGrafana();
		if(authHeader == null){
			authHeader = "Basic "+Base64.getEncoder().encodeToString((grafana.getAdminUserName()+":"+grafana.getAdminUserPassword()).getBytes());
		}
		return authHeader;
	}
}
