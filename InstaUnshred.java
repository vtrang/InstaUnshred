import java.awt.*;
import java.awt.image.MemoryImageSource;
import java.awt.image.PixelGrabber;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

public final class InstaUnshred extends java.applet.Applet{
	private InstaUnshred(){}

	/*
	Un-shreds an image using differences between right & left edges
  @param Image The shredded image to be worked on
  @param int The number of uniformly distributed columns created after shredding
  @return Image The unshredded image after processing
	 */
	public static Image unshred(Image shreddedImage, int numberOfColumns){

		if (numberOfColumns <= 0){
			System.out.println("Number of columns must be a positive integer");
			System.exit(1);
	 }
		else if (numberOfColumns == 1){
			return shreddedImage;
		}

		//Initialization
		Toolkit tk = Toolkit.getDefaultToolkit();
		HashMap<Integer, int[][]> edgeCache;
		Image resultImage;
		int imageWidth = shreddedImage.getWidth(null);
		int imageHeight = shreddedImage.getHeight(null);
		int shredWidth = imageWidth / numberOfColumns;
		int pixels[] = new int[imageWidth*imageHeight];
		PixelGrabber pg = new PixelGrabber(shreddedImage, 0, 0, imageWidth,
						imageHeight, pixels, 0, imageWidth);
		try {
			pg.grabPixels();
		} catch (InterruptedException e) {
			System.out.println("Could not finish grabbing pixels");
			e.printStackTrace();
		}

		if (numberOfColumns == 2){
			//Assumes shreds never arranged correctly for trivial swapping solution
			int swapPixels[] = new int[imageWidth*imageHeight];
			for (int i = 0; i <= i+(imageWidth*(imageHeight-1)); i += imageWidth){
				for (int j = i; j < (imageWidth/2)-1; j++){
					swapPixels[j] = pixels[j+imageWidth];
					swapPixels[j+imageWidth] = pixels[j];
				}
			}
			resultImage = tk.createImage(new MemoryImageSource(imageWidth, imageHeight,
							swapPixels, 0, imageWidth));
			return resultImage;
		}

		//Map shredded columns to their left & right edge color information
		edgeCache = mapEdges(pixels, shredWidth, imageWidth, imageHeight);
		//Get distances using cached edges
		int[][][] distMatrix = getMatrix(edgeCache);
		//Get corrected order of shreds
		LinkedList<Integer> correctedOrder = reorder(distMatrix);
		//Repair image
		pixels = rearrangeColumns(pixels, shredWidth, correctedOrder);
		//Return image
		resultImage = tk.createImage(new MemoryImageSource(imageWidth, imageHeight,
						pixels, 0, imageWidth));
		return resultImage;
	}

	/*
	 Rearranges the shreds
	 @param int[] The pixel array to rearrange
	 @param int The width of each column
	 @param LinkedList<Integer> The order to which it should be rearranged
	 */
	private static int[] rearrangeColumns(int[] pixels, int shredWidth,
																				LinkedList<Integer> orderOfColumns){
		int numberOfColumns = orderOfColumns.size();
		int numberOfPixels = pixels.length;
		int width = numberOfColumns * shredWidth;
		int height = numberOfPixels / width;
		int[] newPixels = new int[numberOfPixels];
		int pixel = 0;
		for (int row = 0; row < height; row++){
			for (int j = 0; j < numberOfColumns; j++){
				for (int k = 0; k < shredWidth; k++){
					newPixels[pixel] = pixels[(k+(shredWidth*orderOfColumns.get(j)))+(width*row)];
					pixel++;
				}
			}
		}
		return newPixels;
	}


	/*
	Caches rgb information along the edges of shredded columns
	@param int The complete image being processed as an array of pixels
	@param int The width of each shredded column
	@param int The width of the image
	@param int The height of the image
	@return HashMap<Integer, int[][]> The mapping of columns to edges
	 */
	private static HashMap<Integer, int[][]> mapEdges(int[] pixels, int shredWidth,
																							int imageWidth, int imageHeight){
		HashMap<Integer, int[][]> edgeCache = new HashMap<Integer, int[][]>();
		for (int column = 0; column < imageWidth/shredWidth; column++){
			int top = column*shredWidth;
			int[][] edges = new int[3*imageHeight][3*imageHeight];
			int arrayCounter = 0;
			for (int i = top; i < top+(imageWidth*(imageHeight-1)); i +=imageWidth){
				int p = pixels[i];
				int r = (p&0xff0000)>>16;
				int g = (p&0xff00)>>8;
				int b = (p&0xff);
				//left edge
				edges[0][arrayCounter] = r;
				edges[0][arrayCounter++] = g;
				edges[0][arrayCounter++] = b;
				arrayCounter -= 2;
				p = pixels[i+(shredWidth-1)];
				r = (p&0xff0000)>>16;
				g = (p&0xff00)>>8;
				b = (p&0xff);
				//right edge
				edges[1][arrayCounter] = r;
				edges[1][arrayCounter++] = g;
				edges[1][arrayCounter++] = b;
				arrayCounter++;
			}
			edgeCache.put(column, edges);
		}
		return edgeCache;
	}
	/*
	Calculates similarity between edges
	@param int[] First edge
	@param int[] Second edge
	@return int The average percentage pythagorean distance of the two edges
	 */
	private static int getDistance(int[] edge1, int[] edge2){
		int sum = 0;
		int arrayCounter = 0;
		for (int i = 0; i < edge1.length; i+=3){
			arrayCounter = i;
			int r1 = edge1[arrayCounter];
			int r2 = edge2[arrayCounter];
			arrayCounter++;
			int g1 = edge1[arrayCounter];
			int g2 = edge2[arrayCounter];
			arrayCounter++;
			int b1 = edge1[arrayCounter];
			int b2 = edge2[arrayCounter];
			double dist = Math.sqrt(Math.pow(Math.abs(r2-r1),2)+Math.pow(Math.abs(g2-g1),2)+Math.pow(Math.abs(b2-b1),2));
			sum += dist;
		}
		return sum;
	}
/*
Records distance of edges to a matrix for later access
@param HashMap<Integer, int[][]> Map of columns to its edges
@return int[][][] Matrix of distances between edges of columns
 */
	private static int[][][] getMatrix(HashMap<Integer, int[][]> edgeCache){
		int size = edgeCache.size();
		int[][][] matchMatrix = new int[size][size][2];
		for (int i = 0; i < size; i++){
			for (int j = 0; j < size; j++){
				int[][] edgeI = edgeCache.get(i);
				int[][] edgeJ = edgeCache.get(j);
				//forward distance
				matchMatrix[i][j][0] = getDistance(edgeI[1], edgeJ[0]);
				//backward distance
				matchMatrix[i][j][1] = getDistance(edgeI[0], edgeJ[1]);
			}
		}
		return matchMatrix;
	}
/*
Makes a guess at the correct rearrangement of columns using distance measurement
@param int[][][] Distance matrix
@return LinkedList<Integer> Listing of columns in preshredded order
 */
	private static LinkedList<Integer> reorder(int[][][] theMatrix){
		LinkedList<Integer> correctOrder = new LinkedList<Integer>();
		ArrayList<Integer> bestFriends = new ArrayList<Integer>();
		int size = theMatrix[0].length;
		int[][] matches = new int[size][2];

		for (int i = 0; i < size; i++){
			int leastRDistance = 999999;
			int leastLDistance = 999999;
			int rightMatch = -1;
			int leftMatch = -1;
			for (int j = 0; j < size; j++){
				int distance = theMatrix[i][j][0];
				if (distance <= leastRDistance && i!=j){
					leastRDistance = distance;
					rightMatch = j;
				}
				distance = theMatrix[i][j][1];
				if (distance <= leastLDistance && i!=j){
					leastLDistance = distance;
					leftMatch = j;
				}
			}
			matches[i][0] = rightMatch;
			matches[i][1] = leftMatch;
		}
		for (int i = 0; i < size; i++){
			int j = matches[(matches[i][0])][1];

			if (j == i){
				if (!bestFriends.contains(i) || !bestFriends.contains(j)){
					bestFriends.add(i);
					bestFriends.add(matches[i][0]);
				}
			}
		}
		correctOrder.add(0);
		int mosDis = 0;
		int rightMos = -1;
		for (int i = 1; i < size; i++){
			int node = correctOrder.peekLast();
			if (node == matches[(matches[node][0])][1]){
				correctOrder.addLast(matches[node][0]);
				int dis = theMatrix[node][matches[node][0]][0];
				if (dis > mosDis){
					mosDis = dis;
					rightMos = node;
				}

			}
			node = correctOrder.peekFirst();
			if (node == matches[matches[node][1]][0]){
				correctOrder.addFirst(matches[node][1]);
			}


		}
		LinkedList<Integer> fixedSeam = new LinkedList<Integer>(  );
		fixedSeam.addAll(correctOrder);
		for (Iterator<Integer> it = correctOrder.iterator(); it.hasNext();){
			int i =it.next();
			if (i != rightMos){
				fixedSeam.addLast(fixedSeam.poll());
			}
			else if (i == rightMos){
				fixedSeam.addLast(fixedSeam.poll());
				break;
			}
		}
		return fixedSeam;
	}
}
