package com.indago.metaseg.ui.view;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public class FeaturesPanel {

	Map< FeatureType, JCheckBox > check = new HashMap<>();

	private JPanel panel;

	public FeaturesPanel() {
		panel = new JPanel();
		panel.setLayout( new GridLayout( FeatureType.values().length, 1, 0, 0 ) );
		panel.setAlignmentY( JComponent.LEFT_ALIGNMENT );

		for ( FeatureType feature : FeatureType.values() ) {
			JCheckBox checkBox = new JCheckBox( feature.getName() );
			check.put( feature, checkBox );
			panel.add( checkBox );
			checkBox.setSelected( true );
			checkBox.addItemListener( new ItemListener() {

				@Override
				public void itemStateChanged( ItemEvent e ) {
				}
			} );
		}
	}

	public FeatureSelection show() {
		int returnValue = JOptionPane.showConfirmDialog( ( Component ) null, panel, "Select Features", JOptionPane.OK_CANCEL_OPTION );

		if ( returnValue == JOptionPane.OK_OPTION ) {
			FeatureSelection fs = new FeatureSelection();
			check.forEach( ( featureType, checkBox ) -> {
				boolean selected = checkBox.isSelected();
				fs.setSelected( featureType, selected );
			} );
			return fs;
		} else {
			return null;
		}
	}

}
