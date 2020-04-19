package com.indago.metaseg.ui.util;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import hr.irb.fastRandomForest.FastRandomForest;

public class ClassifierLoaderAndSaver {

	public static boolean saveRandomForestClassifier( FastRandomForest forest, String default_dir_path ) {
		JFileChooser chooser = new JFileChooser( default_dir_path );
		chooser.showSaveDialog( null );
		boolean classifierSaved = false;
		try {

			if ( !( chooser.getSelectedFile() == null ) ) {

				weka.core.SerializationHelper.write( chooser.getSelectedFile() + ".msmodel", forest );
				classifierSaved = true;
			}

		} catch ( Exception e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return classifierSaved;
	}

	public static FastRandomForest loadRandomForestClassifier( String default_dir_path ) {
		FastRandomForest forest = null;
		JFileChooser chooser = new JFileChooser( default_dir_path );
		FileNameExtensionFilter filter = new FileNameExtensionFilter( "msmodel", "msmodel" );
		chooser.setFileFilter( filter );
		chooser.setAcceptAllFileFilterUsed( false );
		if ( chooser.showOpenDialog( null ) == JFileChooser.APPROVE_OPTION ) {
			File selectedFile = chooser.getSelectedFile();
			try {
				forest = ( FastRandomForest ) weka.core.SerializationHelper.read( selectedFile.getAbsolutePath() );
			} catch ( Exception e ) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return forest;
	}
}
