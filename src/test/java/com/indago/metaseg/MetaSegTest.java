package com.indago.metaseg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.widget.WidgetService;

import com.indago.data.segmentation.LabelingSegment;
import com.indago.metaseg.data.LabelingFrames;
import com.indago.metaseg.io.projectfolder.MetasegProjectFolder;
import com.indago.metaseg.ui.model.MetaSegCostPredictionTrainerModel;
import com.indago.metaseg.ui.model.MetaSegModel;
import com.indago.metaseg.ui.model.MetaSegSolverModel;
import com.indago.plugins.seg.IndagoSegmentationPlugin;
import com.indago.plugins.seg.IndagoSegmentationPluginService;

import io.scif.codec.CodecService;
import io.scif.formats.qt.QTJavaService;
import io.scif.formats.tiff.TiffService;
import io.scif.img.IO;
import io.scif.img.ImgUtilityService;
import io.scif.img.converters.PlaneConverterService;
import io.scif.services.DatasetIOService;
import io.scif.services.FilePatternService;
import io.scif.services.FormatService;
import io.scif.services.InitializeService;
import io.scif.services.JAIIIOService;
import io.scif.services.LocationService;
import io.scif.services.TranslatorService;
import io.scif.xml.XMLService;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.OpMatchingService;
import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.ValuePair;

public class MetaSegTest {

	@Test
	public void testMetaSegPlugins() {
		final Context context =
				new Context( FormatService.class, OpService.class, OpMatchingService.class, IOService.class, DatasetIOService.class, LocationService.class, DatasetService.class, ImgUtilityService.class, StatusService.class, TranslatorService.class, QTJavaService.class, TiffService.class, CodecService.class, JAIIIOService.class, LogService.class, IndagoSegmentationPluginService.class, PlaneConverterService.class, InitializeService.class, XMLService.class, FilePatternService.class, WidgetService.class );
		MetaSegContext.segPlugins = context.getService( IndagoSegmentationPluginService.class );
		assertNotNull( "Seg plugins should not be null", MetaSegContext.segPlugins );
		assertNotNull( "Seg plugins should not be null", MetaSegContext.segPlugins.getPlugins() );
		assertTrue( "Seg plugins should be greater than 0", MetaSegContext.segPlugins.getPlugins().size() > 0 );

	}

	@Test
	public < T extends RealType< T > > void testMetaSegApplication() throws IOException {
		final Context context =
				new Context( FormatService.class, OpService.class, OpMatchingService.class, IOService.class, DatasetIOService.class, LocationService.class, DatasetService.class, ImgUtilityService.class, StatusService.class, TranslatorService.class, QTJavaService.class, TiffService.class, CodecService.class, JAIIIOService.class, LogService.class, IndagoSegmentationPluginService.class, PlaneConverterService.class, InitializeService.class, XMLService.class, FilePatternService.class, WidgetService.class );
		MetaSegContext.segPlugins = context.getService( IndagoSegmentationPluginService.class );
		Img input = IO.openImgs( getClass().getResource( "/metaseg_small/raw.tif" ).getPath() ).get( 0 );
		ImgPlus< T > data = new ImgPlus< T >( input, "input", new AxisType[] { Axes.X, Axes.Y, Axes.TIME } );
		assertEquals( 3, data.numDimensions() );
		assertTrue( data.dimension( 0 ) > 0 );
		assertTrue( data.dimension( 1 ) > 0 );
		assertTrue( data.dimension( 2 ) > 0 );
		MetasegProjectFolder projectFolder = null;
		projectFolder = new MetasegProjectFolder( new File( getClass().getResource( "/metaseg_small" ).getPath() ) );
		projectFolder.initialize();
		assertTrue( projectFolder.exists() );
		final MetaSegModel model = new MetaSegModel( projectFolder, data );

		for ( final String name : MetaSegContext.segPlugins.getPluginNames() ) {
			final IndagoSegmentationPlugin segPlugin =
					MetaSegContext.segPlugins.createPlugin(
							name,
							model.getProjectFolder(),
							model.getRawData(),
							MetaSegLog.segmenterLog );
			if ( segPlugin.isUsable() ) {
				model.getSegmentationModel().addPlugin( segPlugin );
			}
		}
		assertEquals( MetaSegContext.segPlugins.getPluginNames().size(), model.getSegmentationModel().getPlugins().size() );
		//fetch segments:
		MetaSegCostPredictionTrainerModel costTrainerModel = model.getCostTrainerModel();
		assertNotNull( costTrainerModel );
		LabelingFrames scratchLabelings = costTrainerModel.createLabelingsFromScratch();
		assertNotNull( scratchLabelings );
		costTrainerModel.getConflictGraphs();
		costTrainerModel.getConflictCliques();

		//prepare training data
		costTrainerModel.setAllSegAndCorrespTime();
		costTrainerModel.randomizeSegmentsAndPrepData();
//		costTrainerModel.getTrainingData();

		//mark a few good or bad
		List< ValuePair< LabelingSegment, Integer > > segms = costTrainerModel.getAllSegsWithIdAndTime();
		for ( int i = 0; i < 10; i++ ) {
			costTrainerModel.addToGood( segms.get( i ) );
		}
		for ( int i = 10; i < 18; i++ ) {
			costTrainerModel.addToBad( segms.get( i ) );
		}
		costTrainerModel.modifyPredictionSet();

		//compute solution
//		costTrainerModel.bdvRemoveAll();
//		costTrainerModel.bdvAdd( model.getRawData(), "RAW" );
		try {
			costTrainerModel.startTrainingPhase();
		} catch ( Exception e ) {
			e.printStackTrace();
		}
		costTrainerModel.computeAllCosts();
		costTrainerModel.getParentModel().getSolutionModel().run();

		MetaSegLog.segmenterLog.info( "Done!" );

		MetaSegSolverModel solutionModel = model.getSolutionModel();

//		ImgLabeling< SegmentNode, IntType > labeling0 = buildLabelings( 0, solutionModel );
//		ImgLabeling< SegmentNode, IntType > labeling1 = buildLabelings( 1, solutionModel );
//
//		List<ImgLabeling<SegmentNode, IntType>> labellist = new ArrayList<>();
//		labellist.add(labeling0);
//		labellist.add(labeling1);

	}
}