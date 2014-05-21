/**
 * 
 */
package com.soffid.iam.addons.replica.agent;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.soffid.tools.db.schema.Column;
import com.soffid.tools.db.schema.Table;

import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.bootstrap.QueryHelper;
import es.caib.seycon.ng.sync.intf.DatabaseReplicaOfflineChangeRetriever;
import es.caib.seycon.ng.sync.intf.OfflineChange;
import es.caib.seycon.ng.sync.intf.OfflineDatabaseChange;
import es.caib.seycon.ng.sync.intf.OfflinePasswordChange;
import es.caib.seycon.ng.sync.intf.OfflineDatabaseChange.Action;
import es.caib.seycon.ng.sync.replica.DatabaseRepository;

/**
 * @author bubu
 *
 */
public class OfflineChangesRetriever implements DatabaseReplicaOfflineChangeRetriever
{
	Log log = LogFactory.getLog(getClass());

	private Long toLong (Object obj)
	{
		if (obj instanceof BigDecimal)
			return ((BigDecimal) obj).longValue();
		else
			return (Long) obj;
	}
	
	private Boolean toBoolean (Object obj)
	{
		if(obj instanceof Long){
			Long l = (Long) obj;
			if(l == 0)
				return false;
			else
				return true;
		}	
		else
			return (Boolean) obj;
	}
	
	Connection connection;

	public Connection getConnection ()
	{
		return connection;
	}

	public void setConnection (Connection connection)
	{
		this.connection = connection;
	}
	

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.intf.DatabaseReplicaMgr#getOfflineChnages()
	 */
	public List<OfflineChange> getOfflineChanges (Long firstId) throws InternalErrorException, SQLException
	{
		QueryHelper qh = new QueryHelper(connection);
		List<Object[]> rows;
		if (firstId == null)
			rows = qh.selectLimit(
				"SELECT OCH_ID, OCH_DATE, OCH_TYPE, " +
								"OPC_EXPDAT, OPC_MUSCHA, OPC_PASSWD, OPC_ACC_ID, OPC_USU_ID, OPC_DMC_ID, " +
								"ODC_ENTITY, ODC_PKVALUE " +
				"FROM SC_OFFCHA " +
				"ORDER BY OCH_ID",
				new Long(100));
		else
			rows = qh.selectLimit(
				"SELECT OCH_ID, OCH_DATE, OCH_TYPE, " +
								"OPC_EXPDAT, OPC_MUSCHA, OPC_PASSWD, OPC_ACC_ID, OPC_USU_ID, OPC_DMC_ID, " +
								"ODC_ENTITY, ODC_PKVALUE " +
				"FROM SC_OFFCHA " +
				"WHERE OCH_ID >= ? " +
				"ORDER BY OCH_ID",
				new Long(100),
				firstId);
		List<OfflineChange> changes = new LinkedList<OfflineChange>();
		for (Object [] row: rows)
		{
			if ("D".equals(row[2]))
			{
				OfflineDatabaseChange odc = processOfflineDataChange(qh, row);
				if (odc != null) changes.add(odc);
			} else if ("P".equals(row[2]))
			{
				OfflinePasswordChange opc = processOfflinePasswordChange(row);
				if (opc != null) changes.add(opc);
			}
		}
		return changes;
	}

	/**
	 * @param row
	 * @return
	 */
	private OfflinePasswordChange processOfflinePasswordChange (Object[] row)
	{
		OfflinePasswordChange opc = new OfflinePasswordChange();
		opc.setId(toLong( row[0]));
		opc.setDate((Date) row[1]);
		opc.setExpiration((Date)row[3]);
		opc.setMustChange(toBoolean(row[4]));
		opc.setPassword(Password.decode((String)row[5]));
		opc.setAccountId( toLong( row[6] ));
		opc.setUserId( toLong ( row[7] ) );
		opc.setDomainId( toLong ( row[8] ));
		return opc;
	}

	
	/**
	 * @param qh 
	 * @param row
	 * @return
	 * @throws SQLException 
	 * @throws Exception 
	 * @throws IOException 
	 */
	private OfflineDatabaseChange processOfflineDataChange (QueryHelper qh, Object[] row) throws InternalErrorException, SQLException
	{
		OfflineDatabaseChange odc = new OfflineDatabaseChange();
		odc.setId(toLong(row[0]));
		odc.setDate((java.util.Date)row[1]);
		String tableName = (String) row[9];
		odc.setTable(tableName);
		Long pkValue;
		pkValue = toLong(row[10]);
		odc.setPrimaryKeyValue(pkValue);
		odc.setColumns(new LinkedList<String>());
		odc.setValues(new LinkedList<Object>());
		
		Table table;
		try
		{
			table = new DatabaseRepository().getTable(tableName);
		}
		catch (Exception e)
		{
			throw new InternalErrorException ("Error analyzing database model", e);
		}
		if (table == null)
		{
			log.info("Ignoring password change on unknown table "+tableName);
			throw null;
		}
		StringBuffer buffer1 = new StringBuffer();
		for (Column column: table.columns)
		{
			if (column.primaryKey)
			{
				odc.setPrimaryKey(column.name);
			}
			else
			{
				if (buffer1.length() == 0)
					buffer1.append ("SELECT ");
				else
					buffer1.append (", ");
				buffer1.append (column.name);
				odc.getColumns().add(column.name);
			}
		}
		buffer1.append (" FROM ").append (table.name).append(" WHERE ").append(odc.getPrimaryKey()).append("=?");
		List<Object[]> dataRows = qh.select(buffer1.toString(), odc.getPrimaryKeyValue());
		if (dataRows.isEmpty())
		{
			odc.setAction(Action.DELETED_ROW);
			odc.setColumns(null);
		}
		else if (dataRows.size() > 1)
		{
			throw new InternalErrorException ("More than one row for primary key "+tableName+"#"+odc.getPrimaryKeyValue());
		}
		else
		{
			for (Object data: dataRows.get(0))
			{
				odc.getValues().add(data);
			}
		}
		return odc;
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.intf.DatabaseReplicaMgr#removeOfflineChange(java.lang.Long)
	 */
	public void removeOfflineChange (Long id) throws InternalErrorException, SQLException
	{
		QueryHelper qh = new QueryHelper(getConnection());
		List<Object[]> rows = qh.select("SELECT OCH_TYPE, ODC_ENTITY, ODC_PKVALUE " +
						"FROM SC_OFFCHA " +
						"WHERE OCH_ID = ? ", 
						new Object[] {id});
		
		for (Object [] row : rows)
		{
			if ("D".equals(row[0]))
			{
				String table = (String) row[1];
				Long taskId = toLong (row[2]);
				if ("SC_TASQUE".equals(table))
				{
					qh.execute("DELETE FROM SC_TASKLOG WHERE TLO_IDTASQUE=?", taskId);
					qh.execute("DELETE FROM SC_TASQUE WHERE TAS_ID=?", taskId);
				}
			}
		}
		qh.execute("DELETE FROM SC_OFFCHA WHERE OCH_ID=?", id);
	}

}
