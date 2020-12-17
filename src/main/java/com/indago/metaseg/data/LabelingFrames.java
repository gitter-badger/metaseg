/**
 *
 */
package com.indago.metaseg.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.indago.data.segmentation.ConflictGraph;
import com.indago.data.segmentation.LabelingBuilder;
import com.indago.data.segmentation.LabelingPlus;
import com.indago.data.segmentation.LabelingSegment;
import com.indago.data.segmentation.MinimalOverlapConflictGraph;
import com.indago.data.segmentation.XmlIoLabelingPlus;
import com.indago.data.segmentation.filteredcomponents.FilteredComponentTree.Filter;
import com.indago.data.segmentation.filteredcomponents.FilteredComponentTree.MaxGrowthPerStep;
import com.indago.data.segmentation.groundtruth.FlatForest;
import com.indago.io.ProjectFile;
import com.indago.io.ProjectFolder;
import com.indago.metaseg.MetaSegLog;
import com.indago.metaseg.ui.model.MetaSegSegmentationCollectionModel;

import indago.ui.progress.ProgressListener;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import weka.gui.ExtensionFileFilter;

/**
 * @author jug
 */
public class LabelingFrames {

	private final MetaSegSegmentationCollectionModel model;

	// Parameters for FilteredComponentTrees
	private int minHypothesisSize;
	private int maxHypothesisSize;
	private final Filter maxGrowthPerStep;
	private final boolean darkToBright = false;

	private List< LabelingBuilder > frameLabelingBuilders = new ArrayList<>(); // need ensured order
	private final Map< LabelingBuilder, ConflictGraph > mapToConflictGraphs = new LinkedHashMap<>();

	private boolean processedOrLoaded;

	/**
	 *
	 * @param model
	 */
	public LabelingFrames( final MetaSegSegmentationCollectionModel model, final int minHypothesisSize, final int maxHypothesisSize ) {
		this.model = model;

		this.minHypothesisSize = minHypothesisSize;
		this.maxHypothesisSize = maxHypothesisSize;
		maxGrowthPerStep = new MaxGrowthPerStep( maxHypothesisSize );

		processedOrLoaded = false;
	}

	public boolean processFrames() {
		try {
			final List< RandomAccessibleInterval< IntType > > segmentHypothesesImages = getSegmentHypothesesImages();
			if ( segmentHypothesesImages.size() == 0 ) { return false; }
			frameLabelingBuilders = new ArrayList<>();
			final long numberOfFrames = model.getModel().getNumberOfFrames();
			final int timeDimensionIndex = model.getModel().getTimeDimensionIndex();

			for ( int frameId = 0; frameId < numberOfFrames; frameId++ ) {

				final RandomAccessibleInterval< DoubleType > rawFrame = model.getModel().getFrame( frameId );
				final LabelingBuilder labelingBuilder = new LabelingBuilder( rawFrame );
				frameLabelingBuilders.add( labelingBuilder );
				int segCounter = 0;
				for ( final RandomAccessibleInterval< IntType > sumimg : segmentHypothesesImages ) {
					String segmentationSource = Integer.toString( segCounter );

					// hyperslize sum_img to desired frame
					final RandomAccessibleInterval< IntType > sumImgFrame;
					if ( timeDimensionIndex == -1 ) {
						sumImgFrame = sumimg;
					} else {
						final long[] min = Intervals.minAsLongArray( sumimg );
						final long[] max = Intervals.maxAsLongArray( sumimg );
						min[ timeDimensionIndex ] = frameId;
						max[ timeDimensionIndex ] = frameId;
						sumImgFrame = Views.zeroMin( Views.interval( sumimg, min, max ) );
					}

					// build component tree on frame

//					final FilteredComponentTree< IntType > tree =
//							FilteredComponentTree.buildComponentTree(
//									sumImgFrame,
//									new IntType(),
//									minHypothesisSize,
//									maxHypothesisSize,
//									maxGrowthPerStep,
//									darkToBright );
//
//					labelingBuilder.buildLabelingForest( tree, segmentationSource );

					final FlatForest flat = new FlatForest( sumImgFrame, new IntType( 0 ) );
					final Set< FlatForest.Node > filteredRoots = flat.roots().stream()
							.filter( node -> node.size() >= minHypothesisSize && node.size() <= maxHypothesisSize )
							.collect( Collectors.toSet() );

					labelingBuilder.buildLabelingForest( () -> filteredRoots, segmentationSource );
					labelingBuilder.pack();
					segCounter += 1;
				}
			}

			processedOrLoaded = true;

		} catch ( final IllegalAccessException e ) {
			// This happens if getSegmentHypothesesImages() is called but none are there yet...
			processedOrLoaded = false;
		}
		return processedOrLoaded;
	}

	public List< RandomAccessibleInterval< IntType > > getSegmentHypothesesImages()
			throws IllegalAccessException {
		return model.getSumImages();
	}

	public int getNumFrames() {
		return frameLabelingBuilders.size();
	}

	public List< LabelingSegment > getLabelingSegmentsForFrame( final int frameId ) {
		return frameLabelingBuilders.get( frameId ).getSegments();
	}

	/**
	 * Returns the <code>LabelingPlus</code> for the requested frame.
	 *
	 * @param frameId
	 *            integer pointing out the frame id
	 * @return the <code>LabelingPlus</code> requested, or <code>null</code> if
	 *         it does not exists.
	 */
	public LabelingPlus getLabelingPlusForFrame( final int frameId ) {
		if ( frameId < frameLabelingBuilders.size() )
			return frameLabelingBuilders.get( frameId );
		else
			return null;
	}

	public ConflictGraph< LabelingSegment > getConflictGraph( final int frameId ) {
		final LabelingBuilder key = frameLabelingBuilders.get( frameId );
		if ( !mapToConflictGraphs.containsKey( key ) ) {
			mapToConflictGraphs.put( key, new MinimalOverlapConflictGraph( frameLabelingBuilders.get( frameId ) ) );
		}
		return mapToConflictGraphs.get( key );
	}

	public boolean loadFromProjectFolder( final ProjectFolder folder ) {
		frameLabelingBuilders.clear();
		processedOrLoaded = false;
		boolean xml_available = false;
		boolean bson_available = false;
		ExtensionFileFilter extensionFilter = null;
		Collection< ProjectFile > xml_files = folder.getFiles( new ExtensionFileFilter( "xml", "XML files" ) );
		Collection< ProjectFile > bson_files = folder.getFiles( new ExtensionFileFilter( "bson", "BSON files" ) );

		if ( xml_files.isEmpty() && bson_files.isEmpty() ) {
			System.out.println( "Labeling frames unavailable!" );
		} else if ( xml_files.isEmpty() == false && bson_files.isEmpty() == true ) {
			xml_available = true;
		} else if ( xml_files.isEmpty() == true && bson_files.isEmpty() == false ) {
			bson_available = true;
		} else if ( xml_files.isEmpty() == false && bson_files.isEmpty() == false ) {
			xml_available = true;
			bson_available = true;
		}
		
		Collection< ProjectFile > files = xml_available ? xml_files : ( bson_available ? bson_files : Collections.EMPTY_LIST );
		if ( files.isEmpty() ) { return processedOrLoaded; }
		for ( final ProjectFile labelingFrameFile : files ) {

				final File fLabeling = labelingFrameFile.getFile();
				if ( fLabeling.canRead() ) {
					final LabelingPlus labelingPlus = new XmlIoLabelingPlus().loadFromBson( fLabeling.getAbsolutePath() );
					frameLabelingBuilders.add( new LabelingBuilder( labelingPlus ) );
				}
				processedOrLoaded = true;
			}
		return processedOrLoaded;
	}

	public boolean needProcessing() {
		return !processedOrLoaded;
	}

	/**
	 * @param folder
	 *            ProjectFolder instance
	 * @param progressListeners
	 *            please do not hand <code>null</code>. Empty lists are fine
	 *            though.
	 */
	public void saveTo( final ProjectFolder folder, final List< ProgressListener > progressListeners ) {
		for ( final ProgressListener progressListener : progressListeners ) {
			progressListener.resetProgress( "Saving segment hypotheses labelings...", frameLabelingBuilders.size() );
		}

		final String fnPrefix = "labeling_frame";
		int i = 0;
		for ( final LabelingBuilder lb : frameLabelingBuilders ) {
			final String fn = String.format( "%s%04d.xml", fnPrefix, i );
			final String abspath = new File( folder.getFolder(), fn ).getAbsolutePath();
			try {
				new XmlIoLabelingPlus().save( lb, abspath );
			} catch ( final IOException e ) {
				MetaSegLog.segmenterLog.error( String.format( "Could not store labeling_frame%04d.* to project folder!", i ) );
//				e.printStackTrace();
			}

			i++;
			for ( final ProgressListener progressListener : progressListeners ) {
				progressListener.hasProgressed();
			}
		}
	}

	public void setMinSegmentSize( final int minHypothesisSize ) {
		this.minHypothesisSize = minHypothesisSize;
	}

	public void setMaxSegmentSize( final int maxHypothesisSize ) {
		this.maxHypothesisSize = maxHypothesisSize;
	}

	public List< LabelingSegment > getSegments( final int t ) {
		return this.frameLabelingBuilders.get( t ).getSegments();
	}

	public List< List< LabelingSegment > > getSegments() {
		final List< List< LabelingSegment > > ret = new ArrayList<>();

		for ( int t = 0; t < getNumFrames(); t++ ) {
			ret.add( this.frameLabelingBuilders.get( t ).getSegments() );
		}
		return ret;
	}

}
