package com.soffid.iam.addons.replica.agent;

import java.sql.Connection;

import es.caib.seycon.ng.sync.engine.db.DatabaseReplicaOfflineChangeRetrieverFactory;
import es.caib.seycon.ng.sync.intf.DatabaseReplicaOfflineChangeRetriever;

public class OfflineChangesRetrieverFactory extends
		DatabaseReplicaOfflineChangeRetrieverFactory {

	@Override
	public DatabaseReplicaOfflineChangeRetriever newInstance(Connection conn) {
		OfflineChangesRetriever ocr = new OfflineChangesRetriever();
		ocr.setConnection(conn);
		return ocr;
	}

}
