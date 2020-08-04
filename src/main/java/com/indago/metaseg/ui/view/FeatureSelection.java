package com.indago.metaseg.ui.view;

import java.util.EnumSet;
import java.util.Set;

public class FeatureSelection {

	private EnumSet< FeatureType > selectedFeatures = EnumSet.allOf( FeatureType.class );

	public void setSelected( FeatureType featureType, boolean selected ) {
		if ( selected ) {
			selectedFeatures.add( featureType );
		} else {
			selectedFeatures.remove( featureType );
		}

	}

	public boolean isSelected( FeatureType featureType ) {
		return selectedFeatures.contains( featureType );
	}

	public int numberOfSelectedFeatures() {
		// TODO Auto-generated method stub
		return selectedFeatures.size();
	}

	public Set< FeatureType > getSelectedFeatures() {
		// TODO Auto-generated method stub
		return selectedFeatures;
	}
}
