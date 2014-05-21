//
// (C) 2013 Soffid
// 
// This file is licensed by Soffid under GPL v3 license
//

package com.soffid.iam.addons.replica.service;
import com.soffid.mda.annotation.*;

import org.springframework.transaction.annotation.Transactional;

@Service ( translatedName="ReplicaService",
	 translatedPackage="com.soffid.iam.addons.replica.service")
@Depends ({com.soffid.iam.addons.replica.model.OfflineChangeEntity.class,
	es.caib.seycon.ng.model.UsuariEntity.class,
	es.caib.seycon.ng.model.DominiContrasenyaEntity.class,
	es.caib.seycon.ng.model.AccountEntity.class,
	es.caib.seycon.ng.model.TasqueEntity.class})
public abstract class ReplicaService {

	@Transactional(rollbackFor={java.lang.Exception.class})
	public void updated(
		java.lang.Object obj)
		throws es.caib.seycon.ng.exception.InternalErrorException {
	}
	@Transactional(rollbackFor={java.lang.Exception.class})
	public void created(
		java.lang.Object obj)
		throws es.caib.seycon.ng.exception.InternalErrorException {
	}
	@Transactional(rollbackFor={java.lang.Exception.class})
	public void deleted(
		java.lang.Object obj)
		throws es.caib.seycon.ng.exception.InternalErrorException {
	}
	@Transactional(rollbackFor={java.lang.Exception.class})
	public void createOfflineTask(
		java.lang.String user, 
		java.lang.String passwordDomain, 
		es.caib.seycon.ng.comu.Password password, 
		boolean mustChange)
		throws es.caib.seycon.ng.exception.InternalErrorException {
	}
	@Transactional(rollbackFor={java.lang.Exception.class})
	public void createOfflineAccountTask(
		java.lang.String account, 
		java.lang.String dispatcher, 
		es.caib.seycon.ng.comu.Password password, 
		boolean mustChange, 
		@Nullable java.util.Date expirationDate)
		throws es.caib.seycon.ng.exception.InternalErrorException {
	}
}
