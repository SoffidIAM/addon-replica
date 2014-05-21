//
// (C) 2013 Soffid
// 
// This file is licensed by Soffid under GPL v3 license
//

package com.soffid.iam.addons.replica.model;
import com.soffid.mda.annotation.*;

@Entity (table="" ,
		discriminatorValue="D" )
public abstract class OfflineDataChangeEntity extends com.soffid.iam.addons.replica.model.OfflineChangeEntity {

	@Column (name="ODC_ENTITY")
	public java.lang.String entity;

	@Column (name="ODC_PKVALUE")
	public java.lang.Long primaryKeyValue;

}
