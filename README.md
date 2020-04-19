[![](https://travis-ci.com/TrNdy/metaseg.svg?branch=master)](https://travis-ci.com/TrNdy/metaseg)

# metaseg

Example segmentation workflow:
1. Expects a folder with RAW.tif and a subfolder named segmentation
2. Run com.indago.metaseg.MetaSegApplication
3. By default, it shows tab meta-training. In this click fetch segments with the default parameters.
4. In training mode, select any mode.
5. Then start active learning, it will start displaying segments (click Y or N to classify as good/bad)
6. After few iterations, click compute solution. This switches to solution tab. 

For running MetaSeg, put the followinmg in run configurations (change the path to data folder on your computer)
-p /Users/prakash/Desktop/metasegData/c.elegans/metaseg_small
