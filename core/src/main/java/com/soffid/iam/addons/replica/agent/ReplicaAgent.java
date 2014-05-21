/**
 * 
 */
package com.soffid.iam.addons.replica.agent;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.soffid.tools.db.persistence.XmlReader;
import com.soffid.tools.db.schema.Column;
import com.soffid.tools.db.schema.Database;
import com.soffid.tools.db.schema.ForeignKey;
import com.soffid.tools.db.schema.Table;
import com.soffid.tools.db.updater.DBUpdater;
import com.soffid.tools.db.updater.MsSqlServerUpdater;
import com.soffid.tools.db.updater.MySqlUpdater;
import com.soffid.tools.db.updater.OracleUpdater;

import es.caib.seycon.db.LogInfoConnection;
import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.agent.Agent;
import es.caib.seycon.ng.sync.bootstrap.QueryHelper;
import es.caib.seycon.ng.sync.intf.DatabaseReplicaMgr;
import es.caib.seycon.ng.sync.intf.OfflineChange;
import es.caib.seycon.ng.sync.intf.OfflineDatabaseChange;
import es.caib.seycon.ng.sync.intf.OfflineDatabaseChange.Action;
import es.caib.seycon.ng.sync.intf.OfflinePasswordChange;
import es.caib.seycon.ng.sync.replica.DatabaseRepository;
import es.caib.seycon.ng.sync.replica.TableDumpServlet;
import es.caib.seycon.ssl.ConnectionFactory;

/**
 * @author bubu
 * 
 */
public class ReplicaAgent extends Agent implements DatabaseReplicaMgr
{
	
	/**
	 * Hash de conexiones ya establecidas. De esta forma se evita que el agente seycon
	 * abra conexiones sin control debido a problemas de comunicaciones con el servidor
	 */
	static Hashtable hash = new Hashtable();
	private String user;
	private Password password;
	private String url;
	private String schema;
	private boolean modelInitialized = false;
	private boolean modelInitializing = false;

	/**
	 * Obtener una conexión a la base de datos. Si la conexión ya se encuentra
	 * establecida (se halla en el hash de conexiones activas), simplemente se retorna
	 * al método invocante. Si no, registra el driver oracle, efectúa la conexión con
	 * la base de datos y la registra en el hash de conexiones activas
	 * 
	 * @return conexión SQL asociada.
	 * @throws InternalErrorException
	 *             algún error en el proceso de conexión
	 */
	public Connection getConnection () throws InternalErrorException
	{
		Connection conn = (Connection) hash.get(this.getDispatcher().getCodi());
		if (conn == null)
		{
			try
			{
				conn = DriverManager.getConnection(url, user, password.getPassword());
				
				hash.put(this.getDispatcher().getCodi(), conn);
			}
			catch (SQLException e)
			{
				e.printStackTrace();
				throw new InternalErrorException("Error connecting to "+url, e);
			}
		}
		return conn;
	}

	@Override
	public void init () throws Exception
	{
		user = getDispatcher().getParam0();
		password = Password.decode(getDispatcher().getParam1());
		url = getDispatcher().getParam2();
		schema = getDispatcher().getParam3();
		
        try {
            Class c = Class.forName("oracle.jdbc.driver.OracleDriver");
            DriverManager.registerDriver((java.sql.Driver) c.newInstance());
        } catch (Exception e) {
            log.info("Error registrando driver: {}", e, null);
        }
        try {
            Class c = Class.forName("com.mysql.jdbc.Driver");
            DriverManager.registerDriver((java.sql.Driver) c.newInstance());
        } catch (Exception e) {
            log.info("Error registrando driver: {}", e, null);
        }
        try{
        	Class c = Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        	DriverManager.registerDriver((java.sql.Driver) c.newInstance());
        } catch (Exception e) {
        	log.info("Error registrando driver: {}", e, null);
        }


       	modelInitialized = false;        
	}

	private void initializeModel () throws InternalErrorException, Exception,
					UnsupportedEncodingException, RemoteException,
					FileNotFoundException, IOException, ClassNotFoundException
	{
		Connection c = getConnection();
        try {
    		Database db = new XmlReader().parse(new ByteArrayInputStream(schema.getBytes("UTF-8")));
    		Database db2 = new XmlReader().parse(getClass().getResourceAsStream("replica-ddl.xml"));
    		DBUpdater updater = null;
    		if (url.toLowerCase().startsWith("jdbc:mysql"))
    		{
    			updater = new MySqlUpdater();
    		}
    		else if (url.toLowerCase().startsWith("jdbc:oracle"))
    		{
    			updater = new OracleUpdater();
    		}
    		else if (url.toLowerCase().startsWith("jdbc:sqlserver"))
    		{
    			updater = new MsSqlServerUpdater();
    		}
    		else
    		{
    			throw new InternalErrorException ("Unknown database driver");
    		}
    		
    		updater.updateIgnoreForeignKeys(c, db);
    		updater.update(c, db2);
    		for (Table t: db.tables)
    		{
    			if (!t.name.equals("SC_TASQUE") && 
    				!t.name.equals("SC_TASKLOG") &&
    				!t.name.equals("SC_SEQUENCE"))
    				populateTable (c, db, t);
    		}
    		if (! isSequenceStarted())
    		{
    			Long seed = Long.decode(getDispatcher().getParam4());
    			initializeSequence(-seed.longValue(), 10, -10);
    		}

    		updater.update(c, db);
    		
        }
		catch (SQLException e)
		{
			closeConnection ();
			log.info("Error updating database model: "+e.toString());
			throw new InternalErrorException ("Error update database model", e);
        }
	}


	/**
	 * @param i	
	 * @param j
	 * @param k
	 * @throws SQLException 
	 * @throws InternalErrorException 
	 */
	public void initializeSequence (long next, long cache, long increment) throws SQLException, InternalErrorException
	{

		// Query current sequence status
		Connection connection = getConnection();
		try
		{
			// Initialize sequence
			PreparedStatement st2 = connection.prepareStatement("INSERT INTO SC_SEQUENCE (SEQ_NEXT, SEQ_CACHE, SEQ_INCREMENT) VALUES (?, ?, ?)");
			try
			{
				st2.setLong(1,  next);
				st2.setLong(2, cache);
				st2.setLong(3, increment);
				st2.execute();
			}
			finally
			{
				st2.close();
			}
		}
		catch (SQLException e)
		{
			closeConnection ();
			throw e;
		}
	}

	/**
	 * @return
	 * @throws SQLException
	 * @throws InternalErrorException 
	 */
	public boolean isSequenceStarted () throws SQLException, InternalErrorException
	{
		// Query current sequence status
		Connection connection = getConnection();
		try
		{
			PreparedStatement st = connection.prepareStatement("SELECT SEQ_NEXT, SEQ_CACHE, SEQ_INCREMENT FROM SC_SEQUENCE");
			try
			{
				ResultSet rs = st.executeQuery();
				try
				{
					if (!rs.next())
						return false;
					else
						return true;
				}
				finally
				{
					rs.close();
				}
			}
			finally
			{
				st.close();
			}
		}
		catch (SQLException e)
		{
			closeConnection ();
			throw e;
		}
	}

	/**
	 * @param c Database connection
	 * @param db 
	 * @param t
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws InternalErrorException 
	 * @throws FileNotFoundException 
	 * @throws RemoteException 
	 * @throws ClassNotFoundException 
	 */
	private void populateTable (Connection c, Database db, Table t) throws SQLException, RemoteException, FileNotFoundException, InternalErrorException, IOException, ClassNotFoundException
	{
//		System.out.println ("Testing table status: "+t.name);
		QueryHelper qh = new QueryHelper(c);
		List<Object[]> result = qh.select("SELECT RPS_TABLE FROM SCR_REPLSTAT WHERE RPS_TABLE=?", new Object[] {t.name});
		if (result.isEmpty())
		{
			log.info ("Populating table "+t.name);
			URL url = new  URL( "https://"+getServerName()+":"+Config.getConfig().getPort()+"/seycon"+TableDumpServlet.PATH+"?table="+t.name );
			HttpsURLConnection httpsConnection = ConnectionFactory.getConnection(url);
			httpsConnection.connect();
			ObjectInputStream in = new ObjectInputStream ( httpsConnection.getInputStream() );
			
			@SuppressWarnings ("unchecked")
			List<String> cols = (List<String>) in.readObject();
			int pkCol = -1;
			String pkName = null;
			Vector<String> cols2 = new Vector<String>(cols.size());
			for (Column column : t.columns)
			{
				if (column.primaryKey)
				{
					pkName = column.name;
				}
			}
			
			for (int i = 0 ; i < cols.size(); i++)
			{
				if (cols.get(i).equals(pkName))
				{
					pkCol = i;
				}
				else
				{
					cols2.add(cols.get(i));
				}
			}
			
			do
			{
				List<Object> row = (List<Object>) in.readObject();
				if (row == null)
					break;
				Long pkValue = null;
				Vector<Object> values = new Vector<Object>(cols2.size());
				Iterator<Object> itRow = row.iterator();
				for (int i = 0; i < row.size(); i++)
				{
					Object value  = itRow.next();
					if (i == pkCol)
						pkValue = (Long) value;
					else
						values.add(value);
				}
				
				update (t.name, pkName, pkValue, cols2, values);
			} while (true);
			
			in.close ();
			
			qh.execute ("INSERT INTO SCR_REPLSTAT(RPS_TABLE) VALUES (?)", new Object[] {t.name});
		}
	}

	private void ensureModelIsInitialized()
	{
		if (! modelInitialized && ! modelInitializing)
		{
			try {
				modelInitializing = true;
				initializeModel();
				modelInitialized = true;
			} catch (Exception e) {
				log.info("Unable to apply data model :" +e.getMessage());
			} finally {
				modelInitializing = false;
			}
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.intf.DatabaseReplicaMgr#remove(java.lang.String,
	 * java.lang.String, java.lang.Long)
	 */
	public void remove (String tableName, String idColumn, Long value) throws InternalErrorException
	{
		QueryHelper qh = new QueryHelper(getConnection());
		try
		{
			ensureModelIsInitialized();
			qh.execute ("DELETE FROM "+tableName+" WHERE "+idColumn+"=?", new Object[]{ value} );
			ensureModelIsInitialized();
		}
		catch (SQLException e)
		{
			closeConnection ();
			throw new InternalErrorException ("Error executing delete: "+e.toString(), e);
		}
	}

	/**
	 * 
	 */
	private void closeConnection ()
	{
		Connection conn = (Connection) hash.get(this.getDispatcher().getCodi());
		if (conn != null)
		{
			hash.remove(getCodi());
			try
			{
				conn.close();
			}
			catch (SQLException e)
			{
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.intf.DatabaseReplicaMgr#update(java.lang.String,
	 * java.lang.String, java.lang.Long, java.lang.String[], java.lang.Object[])
	 */
	public void update (String tableName, String idColumn, Long idValue,
					Vector<String> columns, Vector<Object> values) throws InternalErrorException
	{
		QueryHelper qh = new QueryHelper(getConnection());
		try
		{
			ensureModelIsInitialized();
			Object values2 [] = new Object[values.size()+1];
			StringBuffer b = null;
			if (tableName.equals("SC_CONTRA"))
				deleteLocalPassword(qh, columns, values);
			if (tableName.equals("SC_ADDPWD"))
				deleteLocalAccountPassword(qh, columns, values);
			if (idColumn != null)
			{
    			b = new StringBuffer("UPDATE ").append(tableName);
    			for (int i = 0; i < columns.size(); i++)
    			{
    				if (i == 0)
    					b.append (" SET ");
    				else
    					b.append (", ");
    				b.append (columns.get(i))
    					.append ("=?");
    				values2[i] = values.get(i);
    			}
    			b.append(" WHERE ").append (idColumn).append("=?");
    			values2[columns.size()] = idValue;
			}
			if (idColumn == null || qh.executeUpdate (b.toString(), values2 ) == 0)
			{
				StringBuffer b2 = new StringBuffer(") VALUES (?");
				StringBuffer b1 = new StringBuffer("INSERT INTO ").append(tableName)
								.append (" (")
								.append (idColumn);
				values2[0] = idValue;
				for (int i = 0; i < columns.size(); i++)
				{
					b1.append (", ");
					b2.append (", ?");
   					b1.append (columns.get(i));
   					values2[i+1] = values.get(i);
				}
				b1.append (b2)
					.append (")");
				qh.executeUpdate (b1.toString(), values2 );
			}
		}
		catch (SQLException e)
		{
			closeConnection ();
			throw new InternalErrorException ("Error executing update: "+e.toString(), e);
		}
	}

	/**
	 * @param qh
	 * @param columns
	 * @param values
	 * @throws SQLException 
	 * @throws InternalErrorException 
	 */
	private void deleteLocalAccountPassword (QueryHelper qh, Vector<String> columns,
					Vector<Object> values) throws SQLException, InternalErrorException
	{
		Long accountId = null;
		Long order = null;
		for (int i = 0; i < columns.size(); i++)
		{
			if (columns.get(i).equals("APW_ACC_ID"))
				accountId = (Long) values.get(i);
			else if (columns.get(i).equals("APW_ORDER"))
				order = (Long) values.get(i);
		}
		
		if (order != null && accountId != null)
		{
			qh.execute("DELETE FROM SC_ACCPWD WHERE APW_ACC_ID=? AND APW_ORDER=?",
							accountId, order);
		}
		else
			throw new InternalErrorException ("Missing columns for ContrasenyaEntity");
		
	}

	/**
	 * @param qh
	 * @param columns
	 * @param values
	 * @throws InternalErrorException 
	 * @throws SQLException 
	 */
	private void deleteLocalPassword (QueryHelper qh, Vector<String> columns,
					Vector<Object> values) throws InternalErrorException, SQLException
	{
		Long userId = null;
		Long domainId = null;
		Long order = null;
		for (int i = 0; i < columns.size(); i++)
		{
			if (columns.get(i).equals("CTR_IDUSU"))
				userId = (Long) values.get(i);
			else if (columns.get(i).equals("CTR_DCN_ID"))
				domainId = (Long) values.get(i);
			else if (columns.get(i).equals("CTR_ORDRE"))
				order = (Long) values.get(i);
		}
		if (order != null && userId != null && domainId != null)
		{
			qh.execute("DELETE FROM SC_CONTRA WHERE CTR_IDUSU=? AND CTR_DCN_ID=? AND CTR_ORDRE=?",
							userId, domainId, order);
		}
		else
			throw new InternalErrorException ("Missing columns for ContrasenyaEntity");
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.intf.DatabaseReplicaMgr#getOfflineChnages()
	 */
	public List<OfflineChange> getOfflineChanges (Long firstId) throws InternalErrorException
	{
		OfflineChangesRetriever ocr = new OfflineChangesRetriever();
		ocr.setConnection(getConnection());
		try {
			return ocr.getOfflineChanges(firstId);
		} 
		catch (SQLException e)
		{
			closeConnection();
			throw new InternalErrorException("Error retrieveing offline change", e);
		}
	}


	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.sync.intf.DatabaseReplicaMgr#removeOfflineChange(java.lang.Long)
	 */
	public void removeOfflineChange (Long id) throws InternalErrorException
	{
		OfflineChangesRetriever ocr = new OfflineChangesRetriever();
		ocr.setConnection(getConnection());
		try {
			ocr.removeOfflineChange(id);
		} 
		catch (SQLException e)
		{
			closeConnection();
			throw new InternalErrorException("Error removing offline change", e);
		}
	}
}
