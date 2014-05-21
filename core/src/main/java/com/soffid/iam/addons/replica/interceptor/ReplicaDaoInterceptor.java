package com.soffid.iam.addons.replica.interceptor;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import org.aopalliance.intercept.Interceptor;
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

public class ReplicaDaoInterceptor implements Interceptor, MethodInterceptor
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
		Method method = mi.getMethod();
		if (method.getParameterTypes().length == 1 &&
			! (mi.getArguments()[0] instanceof Long))
		{
			Object param = mi.getArguments()[0];
			if (method.getName().equals("create") )
			{
				if (param instanceof Collection)
					for (Iterator<Object> iterator = ((Collection) param).iterator(); 
							iterator.hasNext();) {
						Object o = iterator.next();
						replicaService.created(o);
					}
				else
					replicaService.created(param);
			}
			else if (method.getName().equals("update") )
			{
				if (param instanceof Collection)
					for (Iterator<Object> iterator = ((Collection) param).iterator(); 
							iterator.hasNext();) {
						Object o = iterator.next();
						replicaService.updated(o);
					}
				else
					replicaService.updated(param);
			}
			else if (method.getName().equals("remove") )
			{
				if (param instanceof Collection)
					for (Iterator<Object> iterator = ((Collection) param).iterator(); 
							iterator.hasNext();) {
						Object o = iterator.next();
						replicaService.deleted(o);
					}
				else
					replicaService.deleted(param);
			}
		}
		return result;
	}
}
