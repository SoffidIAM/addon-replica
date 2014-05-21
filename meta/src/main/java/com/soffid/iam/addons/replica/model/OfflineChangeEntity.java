//
// (C) 2013 Soffid
// 
// This file is licensed by Soffid under GPL v3 license
//

package com.soffid.iam.addons.replica.model;
import com.soffid.mda.annotation.*;

@Entity (table="SC_OFFCHA" ,
		discriminatorValue="-" ,
		discriminatorColumn="OCH_TYPE" )
public abstract class OfflineChangeEntity {

	@Column (name="OCH_ID")
	@Nullable
	@Identifier
	public java.lang.Long id;

	@Column (name="OCH_DATE")
	public java.util.Date date;

}
