package com.soffid.iam.addons.replica.service;

import com.soffid.iam.addons.replica.agent.OfflineChangesRetrieverFactory;

import es.caib.seycon.ng.sync.engine.db.DatabaseReplicaOfflineChangeRetrieverFactory;


public class ReplicaBootServiceImpl extends ReplicaBootServiceBase {

	@Override
	protected void handleSyncServerBoot() throws Exception {
		System.setProperty(DatabaseReplicaOfflineChangeRetrieverFactory.PROPERTY_NAME,
				OfflineChangesRetrieverFactory.class.getName());
	}

	@Override
	protected void handleConsoleBoot() throws Exception {
	}

}
