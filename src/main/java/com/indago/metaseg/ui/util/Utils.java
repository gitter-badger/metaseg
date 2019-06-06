package com.indago.metaseg.ui.util;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;

public class Utils {

	public static < K, V extends Comparable< ? super V > > Map< K, V > sortByValue( Map< K, V > map ) {
		List< Entry< K, V > > list = new ArrayList<>( map.entrySet() );
		list.sort( Entry.comparingByValue() );

		Map< K, V > result = new LinkedHashMap<>();
		for ( Entry< K, V > entry : list ) {
			result.put( entry.getKey(), entry.getValue() );
		}

		return result;
	}

	public static String getSelectedButtonText( ButtonGroup buttonGroup ) {
		for ( Enumeration< AbstractButton > buttons = buttonGroup.getElements(); buttons.hasMoreElements(); ) {
			AbstractButton button = buttons.nextElement();

			if ( button.isSelected() ) { return button.getText(); }
		}

		return null;
	}

}
