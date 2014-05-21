package com.soffid.iam.addons.replica.interceptor;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.ExtendsQueueEntry;

import com.soffid.iam.addons.replica.service.ReplicaService;
import com.soffid.iam.addons.replica.service.ReplicaServiceImpl;

import es.caib.seycon.ng.comu.AccountType;
import es.caib.seycon.ng.comu.Password;
import es.caib.seycon.ng.comu.UserAccount;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.model.AccountEntity;
import es.caib.seycon.ng.model.DominiContrasenyaEntity;
import es.caib.seycon.ng.model.UserAccountEntity;
import es.caib.seycon.ng.model.UsuariEntity;
import es.caib.seycon.ng.servei.InternalPasswordService;
import es.caib.seycon.ng.sync.engine.ReplicaConnection;

public class ReplicaServiceInterceptor implements MethodInterceptor
{
	private SessionFactory sessionFactory = null;
	private ReplicaService replicaService = null;


	public ReplicaService getReplicaService() {
		return replicaService;
	}

	public void setReplicaService(ReplicaService replicaService) {
		this.replicaService = replicaService;
	}

	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	public Object invoke(MethodInvocation mi) throws Throwable {
		Object result = mi.proceed();
		if (mi.getThis() != null && mi.getThis() instanceof  InternalPasswordService)
		{
			Method method = mi.getMethod();
			if (method.getName().equals("storePassword") &&
				method.getParameterTypes().length == 4 &&
				method.getParameterTypes()[0] == es.caib.seycon.ng.model.UsuariEntity.class &&
				method.getParameterTypes()[1] == DominiContrasenyaEntity.class &&
				method.getParameterTypes()[2] == es.caib.seycon.ng.comu.Password.class && 
				method.getParameterTypes()[3] == boolean.class)
			{
				
		        if (isOffline())
		        {
		        	UsuariEntity ue = (UsuariEntity) mi.getArguments()[0];
		        	DominiContrasenyaEntity dce = (DominiContrasenyaEntity) mi.getArguments()[1];
		        	Password password = (Password) mi.getArguments()[2];
		        	boolean mustChange = (Boolean) mi.getArguments()[3];
		        	replicaService.createOfflineTask(ue.getCodi(), dce.getCodi(), password, mustChange);
		        }
			}
			else if (method.getName().equals("storeAccountPassword") &&
					method.getParameterTypes().length == 4 &&
					method.getParameterTypes()[0] == AccountEntity.class &&
					method.getParameterTypes()[1] == Password.class &&
					method.getParameterTypes()[2] == boolean.class && 
					method.getParameterTypes()[3] == Date.class)
			{
					
		        if (isOffline())
		        {
		        	AccountEntity acc = (AccountEntity) mi.getArguments()[0];
		        	Password password = (Password) mi.getArguments()[1];
		        	boolean mustChange = (Boolean) mi.getArguments()[2];
		        	Date expirationDate = (Date) mi.getArguments()[3];
		    		if (acc.getType().equals(AccountType.USER))
		    		{
		    			for (UserAccountEntity ua: acc.getUsers())
		    			{
				        	replicaService.createOfflineTask(ua.getUser().getCodi(), acc.getDispatcher().getDomini().getCodi(), password, mustChange);
		    			}
		    		}
		    		else
		    		{
		    			replicaService.createOfflineAccountTask(acc.getName(), acc.getDispatcher().getCodi(), password, mustChange, expirationDate);
		    		}
		        }
			}
		}
		return result;
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
