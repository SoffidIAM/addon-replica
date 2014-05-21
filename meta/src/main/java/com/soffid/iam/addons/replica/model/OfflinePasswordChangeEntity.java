//
// (C) 2013 Soffid
// 
// This file is licensed by Soffid under GPL v3 license
//

package com.soffid.iam.addons.replica.model;
import com.soffid.mda.annotation.*;

@Entity (table="" ,
		discriminatorValue="P" )
@Depends ({es.caib.seycon.ng.model.DominiContrasenyaEntity.class,
	es.caib.seycon.ng.model.UsuariEntity.class,
	es.caib.seycon.ng.model.AccountEntity.class})
public abstract class OfflinePasswordChangeEntity extends com.soffid.iam.addons.replica.model.OfflineChangeEntity {

	@Column (name="OPC_EXPDAT")
	@Nullable
	public java.util.Date expirationDate;

	@Column (name="OPC_STATUS")
	public java.lang.String status;

	@Column (name="OPC_PASSWD", length=255)
	public java.lang.String password;

	@Column (name="OPC_MUSCHA")
	public boolean mustChange;

	@Column (name="OPC_DMC_ID")
	@Nullable
	public es.caib.seycon.ng.model.DominiContrasenyaEntity passwordDomain;

	@Column (name="OPC_USU_ID")
	@Nullable
	public es.caib.seycon.ng.model.UsuariEntity user;

	@Column (name="OPC_ACC_ID")
	@Nullable
	public es.caib.seycon.ng.model.AccountEntity account;

}
