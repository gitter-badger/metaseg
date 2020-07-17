package com.indago.metaseg.threadedfeaturecomputation;

public class FeaturesRow {
	
	final int time;
	final double area;
	final double perimeter;
	final double convexity;
	final double circularity;
	final double solidity;
	final double boundarysizeconvexhull;
//	final double elongation;
	final double normalizedBoundaryPixelSum;
	final double normalizedFacePixelSum;

	FeaturesRow(
			int time,
			double area,
			double perimeter,
			double convexity,
			double circularity,
			double solidity,
			double boundarySizeConvexHull,
//			double elongation,
			double normalizedBoundaryPixelSum,
			double normalizedFacePixelSum ) {
		this.time = time;
		this.area = area;
		this.perimeter = perimeter;
		this.convexity = convexity;
		this.circularity = circularity;
		this.solidity = solidity;
		this.boundarysizeconvexhull = boundarySizeConvexHull;
//		this.elongation = elongation;
		this.normalizedBoundaryPixelSum = normalizedBoundaryPixelSum;
		this.normalizedFacePixelSum = normalizedFacePixelSum;
	}

	public double getArea() {
		return area;
	}

	public double getPerimeter() {
		return perimeter;
	}

	public double getConvexity() {
		return convexity;
	}

	public double getCircularity() {
		return circularity;
	}

	public double getSolidity() {
		return solidity;
	}

	public double getBoundarysizeconvexhull() {
		return boundarysizeconvexhull;
	}

	public double getNormalizedBoundaryPixelSum() {
		return normalizedBoundaryPixelSum;
	}

	public double getNormalizedFacePixelSum() {
		return normalizedFacePixelSum;
	}

//	public double getElongation() {
//		return elongation;
//	}

}
