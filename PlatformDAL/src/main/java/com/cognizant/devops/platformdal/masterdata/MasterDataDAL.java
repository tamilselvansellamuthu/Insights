/*******************************************************************************
 * Copyright 2017 Cognizant Technology Solutions
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package com.cognizant.devops.platformdal.masterdata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;

import com.cognizant.devops.platformdal.core.BaseDAL;

public class MasterDataDAL extends BaseDAL {

	private static final Logger log = LogManager.getLogger(MasterDataDAL.class);

	public void processMasterDataQuery(String query) {
		log.debug("query  {} ", query);
		try (Session session = getSessionObj()) {
			session.beginTransaction();
			NativeQuery createSQLQuery = session.createSQLQuery(query);
			createSQLQuery.executeUpdate();
			session.getTransaction().commit();
		} catch (Exception e) {
			log.error(e);
			throw e;
		}
	}
}