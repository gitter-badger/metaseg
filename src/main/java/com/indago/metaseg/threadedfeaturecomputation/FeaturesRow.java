package com.indago.metaseg.threadedfeaturecomputation;

import com.indago.metaseg.ui.view.FeatureType;

public class FeaturesRow {
	
	final int time;
	final double[] values;

	FeaturesRow( int time ) {
		this.time = time;
		this.values = new double[ FeatureType.values().length ];
	}

	public void setValue( FeatureType featureType, double value ) {
		values[ featureType.ordinal() ] = value;
	}

	public double getValue( FeatureType featureType ) {
		return values[ featureType.ordinal() ];
	}
}
