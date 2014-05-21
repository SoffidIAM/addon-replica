/**
 * 
 */
package com.soffid.iam.addons.replica.service;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

import org.hibernate.EntityMode;
import org.hibernate.SessionFactory;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.soffid.iam.addons.replica.model.OfflineChangeEntity;
import com.soffid.iam.addons.replica.model.OfflineDataChangeEntity;
import com.soffid.iam.addons.replica.model.OfflinePasswordChangeEntity;

import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.model.TaskLogEntity;
import es.caib.seycon.ng.model.TasqueEntity;
import es.caib.seycon.ng.sync.engine.ReplicaConnection;
import es.caib.seycon.ng.sync.engine.TaskHandler;

/**
 * @author bubu
 *
 */
public class ReplicaServiceImpl extends ReplicaServiceBase implements ApplicationContextAware, InitializingBean
{
	private ApplicationContext applicationContext;
	private SessionFactory sessionFactory = null;

	/* (non-Javadoc)
	 * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
	 */
	public void setApplicationContext (ApplicationContext applicationContext)
					throws BeansException
	{
		this.applicationContext = applicationContext;
		
	}
	
	private SessionFactory getSessionFactory () 
	{
		if (sessionFactory == null)
			sessionFactory = (SessionFactory) applicationContext.getBean("sessionFactory");
		return sessionFactory;
	}

	static final String[] allowedOfflineTables = new String [] {
			"SC_SESSIO",
			"SC_SECRET",
			"SC_MAQUIN",
			"SC_REGACC",
			"SC_AUDITO",
			"SC_TASQUE"
	};
	
	static final String[] ignoredOfflineTables = new String [] {
		"SC_CONTRA",
		"SC_ACCPWD",
		"SC_ACCOUN",
		"SC_TASKLOG",
		"SC_OFFCHA"
	};

	private void createTask (String taskName, Object entity) throws IllegalArgumentException, SecurityException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, InternalErrorException
	{
		if ("true".equals(System.getProperty("soffid.replica.enabled") ))
		{
			Class cl = entity.getClass();
			while (cl != null)
			{
				ClassMetadata md = getSessionFactory().getClassMetadata(cl);
				if (md == null)
					cl = cl.getSuperclass();
				else
				{
					AbstractEntityPersister aep = (AbstractEntityPersister) md;
					String[] cn = aep.getIdentifierColumnNames();
					String tn = aep.getTableName();
					Serializable id = aep.getIdentifier(entity, EntityMode.POJO);
					
					if (id instanceof Long)
					{
		    			Connection connection = getSessionFactory().getCurrentSession().connection();
		    			ReplicaConnection rc;
		    			try {
    		    			rc =  connection.unwrap(ReplicaConnection.class);
    		    			if (rc != null && !rc.isMainDatabase())
		    				{
		    					for (String s: allowedOfflineTables)
		    					{
		    						if (s.equals(tn))
		    						{
		    							OfflineDataChangeEntity odce = getOfflineChangeEntityDao().newOfflineDataChangeEntity();
		    							odce.setDate(new Date());
		    							odce.setEntity(tn);
		    							odce.setPrimaryKeyValue((Long) id);
		    							getOfflineChangeEntityDao().create(odce);
		    							return;
		    						}
		    					}
		    					for (String s: ignoredOfflineTables)
		    					{
		    						if (s.equals(tn))
		    						{
		    							return;
		    						}
		    					}
		    					throw new IllegalArgumentException("Cannot modify table "+tn+" on backup database");
		    				}
		    			} catch (SQLException e) {
		    				throw new InternalErrorException("Error unwrapping connection", e);
		    			} catch (AbstractMethodError e) {
		    				// Method not foumd or something else
		    			}
		        		if (entity instanceof TasqueEntity || entity instanceof TaskLogEntity || entity instanceof OfflineChangeEntity )  
		        		{
		        			// Nothing to do
		        		}
		        		else
		        		{
        		    		TasqueEntity tasque = getTasqueEntityDao().newTasqueEntity();
        		    		tasque.setTransa(taskName);
        		    		tasque.setPrimaryKeyValue((Long) id);
        		    		tasque.setEntity(tn);
        		    		tasque.setData(new Timestamp(System.currentTimeMillis()));
        		    		getTasqueEntityDao().create(tasque);
		        		}
					}
					break;
    			}
    
    		}
		}
	}
	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.model.ReplicaServiceBase#handleCreated(java.lang.Object)
	 */
	@Override
	protected void handleCreated (Object obj) throws Exception
	{
		if (obj instanceof TasqueEntity)
		{
			TasqueEntity tasque = (TasqueEntity) obj;
        	if (tasque.getTransa().equals(TaskHandler.UPDATE_USER_PASSWORD) && isOffline())
        	{
        		createOfflineTask(tasque.getUsuari(), tasque.getDominiContrasenyes(), Password.decode(tasque.getContra()), "S".equals(tasque.getCancon()));
        	}
        	else if (tasque.getTransa().equals(TaskHandler.UPDATE_ACCOUNT_PASSWORD) && isOffline())
        	{
        		createOfflineAccountTask(tasque.getUsuari(), tasque.getCoddis(), Password.decode(tasque.getContra()), "S".equals(tasque.getCancon()),
        				tasque.getExpirationDate());
        	}
		}
		else
			createTask (TaskHandler.UPDATE_OBJECT, obj);
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.model.ReplicaServiceBase#handleDeleted(java.lang.Object)
	 */
	@Override
	protected void handleDeleted (Object obj) throws Exception
	{
		createTask (TaskHandler.DELETE_OBJECT, obj);
	}

	/* (non-Javadoc)
	 * @see es.caib.seycon.ng.model.ReplicaServiceBase#handleUpdated(java.lang.Object)
	 */
	@Override
	protected void handleUpdated (Object obj) throws Exception
	{
		createTask (TaskHandler.UPDATE_OBJECT, obj);
	}

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
	 */
	public void afterPropertiesSet () throws Exception
	{
		
	}

	@Override
	protected void handleCreateOfflineTask(String user, String passwordDomain,
			Password password, boolean mustChange) throws Exception {
		OfflinePasswordChangeEntity opc = getOfflineChangeEntityDao().newOfflinePasswordChangeEntity();
		opc.setUser(getUsuariEntityDao().findByCodi(user));
		opc.setPasswordDomain(getDominiContrasenyaEntityDao().findByCodi(passwordDomain));
		opc.setDate(new Date());
		opc.setMustChange("S".equals(mustChange));
		opc.setPassword(password.toString());
		opc.setStatus("P");
		getOfflineChangeEntityDao().create(opc);
	}

	@Override
	protected void handleCreateOfflineAccountTask(String account,
			String dispatcher, Password password, boolean mustChange,
			Date expirationDate) throws Exception {
		OfflinePasswordChangeEntity opc = getOfflineChangeEntityDao().newOfflinePasswordChangeEntity();
		opc.setAccount(getAccountEntityDao().findByNameAndDispatcher(account, dispatcher));
		opc.setDate(new Date());
		opc.setExpirationDate(expirationDate);
		opc.setMustChange("S".equals(mustChange));
		opc.setPassword(password.toString());
		opc.setStatus("P");
		getOfflineChangeEntityDao().create(opc);
	}

	private boolean isOffline () throws InternalErrorException 
	{
		Connection connection = getSessionFactory().getCurrentSession().connection();
		ReplicaConnection rc;
		try {
			rc =  connection.unwrap(ReplicaConnection.class);
			if (rc != null)
				return !rc.isMainDatabase();
		} catch (SQLException e) {
			throw new InternalErrorException("Error unwrapping connection", e);
		} catch (AbstractMethodError e) {
			// Method not found or something else
		}
		return false;
	}


}
