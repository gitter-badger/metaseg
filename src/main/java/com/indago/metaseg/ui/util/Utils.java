package com.indago.metaseg.ui.util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;

import com.indago.io.ProjectFile;

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

	public static int[] uniqueRand( int n, int m ) { //Choose n unique random numbers from 0 to m-1
		Random rand = new Random();
		int[] r = new int[ n ];
		int[] result = new int[ n ];
		for ( int i = 0; i < n; i++ ) {
			r[ i ] = rand.nextInt( m - i );
			result[ i ] = r[ i ];
			for ( int j = i - 1; j >= 0; j-- ) {
				if ( result[ i ] >= r[ j ] )
					result[ i ]++;
			}
		}
		return result;
	}

	public static Map< Integer, Double > readProblemGraphFileAndCreateCostIdMap( ProjectFile pgFile ) {
		Map< Integer, Double > mapId2Costs = new HashMap< Integer, Double >();
		try (BufferedReader br = new BufferedReader( new FileReader( pgFile.getFile() ) )) {
			String line;
			while ( ( line = br.readLine() ) != null ) {
				if ( line.startsWith( "H" ) ) {
					String[] columns = line.split( "\\s+" );
					int id = Integer.parseInt( columns[ 2 ] );
					double costOfId = Double.parseDouble( columns[ 3 ] );
					mapId2Costs.put( id, costOfId );
				}
			}
		} catch ( FileNotFoundException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch ( IOException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return mapId2Costs;
	}

	public static Map< String, Integer > returnFrequencies( List< String > segSourcesPerTime ) {
		Map< String, Integer > hm = new HashMap< String, Integer >();

		for ( String i : segSourcesPerTime ) {
			Integer j = hm.get( i );
			hm.put( i, ( j == null ) ? 1 : j + 1 );
		}
		return hm;
	}

}
