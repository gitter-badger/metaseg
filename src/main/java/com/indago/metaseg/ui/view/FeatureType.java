package com.indago.metaseg.ui.view;


public enum FeatureType {

	AREA( "area" ), PERIMETER( "perimeter" ), CONVEXITY( "convexity" ), CIRCULARITY( "circularity" ), SOLIDITY( "solidity" ), BOUNDARY_SIZE_CONVEX_HULL( "perimeter of convex hull" ), NORMALIZED_BOUNDARY_PIXEL_SUM( "average boundary pixel intensity" ), NORMALIZED_FACE_PIXEL_SUM( "average face pixel intensity" );

	private final String name;

	FeatureType( String name ) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
}
