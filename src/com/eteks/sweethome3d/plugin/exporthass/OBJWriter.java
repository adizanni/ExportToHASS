/*
 * OBJWriter.java 18 sept. 2008
 *
 * Sweet Home 3D, Copyright (c) 2008 Emmanuel PUYBARET / eTeks <info@eteks.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.eteks.sweethome3d.plugin.exporthass;

import java.awt.image.RenderedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.regex.*;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.media.j3d.Appearance;
import javax.media.j3d.ColoringAttributes;
import javax.media.j3d.Geometry;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.GeometryStripArray;
import javax.media.j3d.Group;
import javax.media.j3d.ImageComponent2D;
import javax.media.j3d.IndexedGeometryArray;
import javax.media.j3d.IndexedGeometryStripArray;
import javax.media.j3d.IndexedLineArray;
import javax.media.j3d.IndexedLineStripArray;
import javax.media.j3d.IndexedQuadArray;
import javax.media.j3d.IndexedTriangleArray;
import javax.media.j3d.IndexedTriangleFanArray;
import javax.media.j3d.IndexedTriangleStripArray;
import javax.media.j3d.LineArray;
import javax.media.j3d.LineStripArray;
import javax.media.j3d.Link;
import javax.media.j3d.Material;
import javax.media.j3d.Node;
import javax.media.j3d.PolygonAttributes;
import javax.media.j3d.QuadArray;
import javax.media.j3d.RenderingAttributes;
import javax.media.j3d.Shape3D;
import javax.media.j3d.TexCoordGeneration;
import javax.media.j3d.Texture;
import javax.media.j3d.TextureAttributes;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.TransparencyAttributes;
import javax.media.j3d.TriangleArray;
import javax.media.j3d.TriangleFanArray;
import javax.media.j3d.TriangleStripArray;
import javax.swing.JOptionPane;
import javax.vecmath.Color3f;
import javax.vecmath.Point3f;
import javax.vecmath.TexCoord2f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import com.eteks.sweethome3d.j3d.HomePieceOfFurniture3D;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;

/**
 * An output stream that writes Java 3D nodes at OBJ + MTL format.
 * <p>Once you wrote nodes, call <code>close</code> method to create the MTL file
 * and the texture images in the same folder as OBJ file. This feature applies
 * only to constructor that takes a file as parameter.<br>
 * Note: this class is compatible with Java 3D 1.3.
 * @author Emmanuel Puybaret
 */
public class OBJWriter extends FilterWriter {
  private final NumberFormat defaultNumberFormat =
      new DecimalFormat("0.#######", new DecimalFormatSymbols(Locale.US));
  private final NumberFormat numberFormat;
  private final String  header;
  public String objects;
  
  private boolean firstNode = true;
  private String  mtlFileName;
  public String  jsonFileName;

  private int shapeIndex = 1;
  private int objectIndex = 1;
  private Map<Point3f, Integer>    vertexIndices = new HashMap<Point3f, Integer>();
  private Map<Vector3f, Integer>   normalIndices = new HashMap<Vector3f, Integer>();
  private Map<TexCoord2f, Integer> textureCoordinatesIndices = new HashMap<TexCoord2f, Integer>();
  private Map<ComparableAppearance, String> appearances =
      new LinkedHashMap<ComparableAppearance, String>();
  private Map<Texture, File> textures = new HashMap<Texture, File>();
  private List<URL>          copiedTextures = new ArrayList<URL>();

  /**
   * Create an OBJ writer for the given file, with no header and default precision.
   */
  public OBJWriter(File objFile) throws FileNotFoundException, IOException {
    this(objFile, null, -1);
  }

  /**
   * Create an OBJ writer for the given file.
   * @param objFile the file into which 3D nodes will be written at OBJ format
   * @param header  a header written as a comment at start of the OBJ file and its MTL counterpart
   * @param maximumFractionDigits the maximum digits count used in fraction part of numbers,
   *                or -1 for default value. Using -1 may cause writing nodes to be twice faster.
   */
  public OBJWriter(File objFile, String header,
                   int maximumFractionDigits) throws FileNotFoundException, IOException {
    this(objFile.toString(), header, maximumFractionDigits);
  }

  /**
   * Create an OBJ writer for the given file name, with no header and default precision.
   */
  public OBJWriter(String objFileName) throws FileNotFoundException, IOException {
    this(objFileName, null, -1);
  }

  /**
   * Create an OBJ writer for the given file name.
   * @param objFileName the name of the file into which 3D nodes will be written at OBJ format
   * @param header  a header written as a comment at start of the OBJ file and its MTL counterpart
   * @param maximumFractionDigits the maximum digits count used in fraction part of numbers,
   *                or -1 for default value. Using -1 may cause writing nodes to be twice faster.
   */
  public OBJWriter(String objFileName, String header,
                   int maximumFractionDigits) throws FileNotFoundException, IOException {
    this(new FileOutputStream(objFileName), header, maximumFractionDigits);
    if (objFileName.toLowerCase().endsWith(".obj")) {
      this.mtlFileName = objFileName.substring(0, objFileName.length() - 4) + ".mtl";
      this.jsonFileName = objFileName.substring(0, objFileName.length() - 4) + ".json";
    } else {
      this.mtlFileName = objFileName + ".mtl";
      this.jsonFileName = objFileName + ".json";
    }
    // Remove spaces in MTL file name
    objects = "{ \n";
    this.mtlFileName = new File(new File(this.mtlFileName).getParent(),
        new File(this.mtlFileName).getName().replace(' ', '_')).toString();
    // Ensure MTL file is using only ASCII codes
    String name = new File(this.mtlFileName).getName();
    for (int i = 0; i < name.length(); i++) {
      if (name.charAt(i) >= 128) {
        this.mtlFileName = new File(new File(this.mtlFileName).getParent(),
            "materials.mtl").toString();
        break;
      }
    }
  }

  /**
   * Create an OBJ writer that will writes in <code>out</code> stream,
   * with no header and default precision.
   */
  public OBJWriter(OutputStream out) throws IOException {
    this(out, null, -1);
  }

  /**
   * Create an OBJ writer that will writes in <code>out</code> stream.
   * @param out the stream into which 3D nodes will be written at OBJ format
   * @param header  a header written as a comment at start of the stream
   * @param maximumFractionDigits the maximum digits count used in fraction part of numbers,
   *                or -1 for default value. Using -1 may cause writing nodes to be twice faster.
   */
  public OBJWriter(OutputStream out, String header,
                   int maximumFractionDigits) throws IOException {
    this(new OutputStreamWriter(new BufferedOutputStream(out), "US-ASCII"), header, maximumFractionDigits);
  }

  /**
   * Create an OBJ writer that will writes in <code>out</code> stream,
   * with no header and default precision.
   */
  public OBJWriter(Writer out) throws IOException {
    this(out, null, -1);
  }

  /**
   * Create an OBJ writer that will writes in <code>out</code> stream.
   * @param out the stream into which 3D nodes will be written at OBJ format
   * @param header  a header written as a comment at start of the stream
   * @param maximumFractionDigits the maximum digits count used in fraction part of numbers,
   *                or -1 for default value. Using -1 may cause writing nodes to be twice faster.
   */
  public OBJWriter(Writer out, String header,
                   int maximumFractionDigits) throws IOException {
    super(out);
    if (maximumFractionDigits >= 0) {
      this.numberFormat = NumberFormat.getNumberInstance(Locale.US);
      this.numberFormat.setMinimumFractionDigits(0);
      this.numberFormat.setMaximumFractionDigits(maximumFractionDigits);
    } else {
      this.numberFormat = null;
    }
    this.header = header;
    writeHeader(this.out);
  }

  /**
   * Writes header to <code>writer</code>
   */
  private void writeHeader(Writer writer) throws IOException {
    if (this.header != null) {
      if (!this.header.startsWith("#")) {
        writer.write("# ");
      }
      writer.write(this.header.replace("\n", "\n# "));
      writer.write("\n");
    }
  }

  /**
   * Write a single character in a comment at OBJ format.
   */
  @Override
  public void write(int c) throws IOException {
    this.out.write("# ");
    this.out.write(c);
    this.out.write("\n");
  }

  /**
   * Write a portion of an array of characters in a comment at OBJ format.
   */
  @Override
  public void write(char cbuf[], int off, int len) throws IOException {
    this.out.write("# ");
    this.out.write(cbuf, off, len);
    this.out.write("\n");
  }

  /**
   * Write a portion of a string in a comment at OBJ format.
   */
  @Override
  public void write(String str, int off, int len) throws IOException {
    this.out.write("# ");
    this.out.write(str, off, len);
    this.out.write("\n");
  }

  /**
   * Write a string in a comment at OBJ format.
   */
  @Override
  public void write(String str) throws IOException {
    this.out.write("# ");
    this.out.write(str, 0, str.length());
    this.out.write("\n");
  }

  /**
   * Throws an <code>InterruptedRecorderException</code> exception
   * if current thread is interrupted.
   */
  private void checkCurrentThreadIsntInterrupted() throws InterruptedIOException {
    if (Thread.interrupted()) {
      this.mtlFileName = null;
      throw new InterruptedIOException("Current thread interrupted");
    }
  }

  /**
   * Writes all the 3D shapes children of <code>node</code> at OBJ format.
   * If there are transformation groups on the path from <code>node</code> to its shapes,
   * they'll be applied to the coordinates written on output.
   * The <code>node</code> shouldn't be alive or if it's alive it should have the
   * capabilities to read its children, the geometries and the appearance of its shapes.
   * Only geometries which are instances of <code>GeometryArray</code> will be written.
   * @param node a Java 3D node
   * @throws IOException if the operation failed
   * @throws InterruptedIOException if the current thread was interrupted during this operation.
   *         The interrupted status of the current thread is cleared when this exception is thrown.
   */
  public void writeNode(Node node) throws IOException, InterruptedIOException {
    writeNode(node, null);
  }

  /**
   * Writes all the 3D shapes children of <code>node</code> at OBJ format.
   * If there are transformation groups on the path from <code>node</code> to its shapes,
   * they'll be applied to the coordinates written on output.
   * The <code>node</code> shouldn't be alive or if it's alive, it should have the
   * capabilities to read its children, the geometries and the appearance of its shapes.
   * Only geometries which are instances of <code>GeometryArray</code> will be written.
   * @param node     a Java 3D node
   * @param nodeName the name of the node. This is useful to distinguish the objects
   *                 names in output. If this name is <code>null</code> or isn't built
   *                 with A-Z, a-z, 0-9 and underscores, it will be ignored.
   * @throws IOException if the operation failed
   * @throws InterruptedIOException if the current thread was interrupted during this operation
   *         The interrupted status of the current thread is cleared when this exception is thrown.
   */
  public void writeNode(Node node, String nodeName) throws IOException, InterruptedIOException {
    if (this.firstNode) {
      if (this.mtlFileName != null) {
        this.out.write("mtllib " + new File(this.mtlFileName).getName() + "\n");
      }
      this.firstNode = false;
    }

    writeNode(node, nodeName, new Transform3D());
  }

  /**
   * Writes all the 3D shapes children of <code>node</code> at OBJ format.
   */
  private void writeNode(Node node, String nodeName, Transform3D parentTransformations ) throws IOException {
    if (node instanceof Group) {
      
      if (node instanceof TransformGroup) {
        parentTransformations = new Transform3D(parentTransformations);
        Transform3D transform = new Transform3D();
        ((TransformGroup)node).getTransform(transform);
        parentTransformations.mul(transform);
      }
      // Write all children
      if ( (node.getName() != null) && (node.getName() != "") ) {
    	  nodeName = node.getName().replace(" ", "_");
    	  this.shapeIndex = 1;

      }

      Enumeration<?> enumeration = ((Group)node).getAllChildren();
      
      while (enumeration.hasMoreElements()) {
        writeNode((Node)enumeration.nextElement(), nodeName, parentTransformations);
      }
    } else if (node instanceof Link) {
      writeNode(((Link)node).getSharedGroup(), nodeName, parentTransformations);
    } else if (node instanceof Shape3D) {
      Shape3D shape = (Shape3D)node;
      Appearance appearance = shape.getAppearance();
      RenderingAttributes renderingAttributes = appearance != null
          ? appearance.getRenderingAttributes() : null;
      if (shape.numGeometries() >= 1
          && (renderingAttributes == null
              || renderingAttributes.getVisible())) {
        // Build a unique human readable object name
      String nodeNameTemp = nodeName.replace("#", "");
      String objectName = "";
      if (accept(nodeNameTemp)) {
        objectName = nodeNameTemp + "_";
      }
/*
      String shapeName = null;
      if (shape.getUserData() instanceof String) {
    	  shapeName = (String)shape.getUserData();
      }
      if (accept(shapeName)) {
    	  objectName += shapeName + "_";
      }
*/
      objectName += String.valueOf(this.shapeIndex++);
  
        // Start a new object at OBJ format
  
      Pattern p = Pattern.compile("lvl\\d{3}(.*)");
      Matcher m; 
      
      if (nodeName.endsWith("#")) {
    	  if (shapeIndex == 2) {
    		  String nodeNameNew = nodeName.replace("#", "");
    		  this.out.write("g " + nodeNameNew + "\n");
    		  m = p.matcher(nodeNameNew);
    		  if (m.matches()) {
    			objects += " \"" + nodeNameNew.substring(6) + "\" : { \"object_id\": \"" + nodeNameNew.substring(6) + "\" },\n";
    		  }
    		  else {
    			objects += " \"" + nodeNameNew + "\" : { \"object_id\": \"" + nodeNameNew + "\" },\n";
    		  }
    	  }
      } else {
    	  this.out.write("g " + objectName + "\n");
    	  m = p.matcher(objectName);
    	  if (m.matches()) {
    		  objects += " \"" + objectName.substring(6) + "\" : { \"object_id\": \"" + objectName.substring(6) + "\" },\n";
    	  } else {
    		  objects += " \"" + objectName + "\" : { \"object_id\": \"" + objectName + "\" },\n";
    	  }
      }        
      
        TexCoordGeneration texCoordGeneration = null;
        Transform3D textureTransform = new Transform3D();
        if (this.mtlFileName != null) {
          if (appearance != null) {
            texCoordGeneration = appearance.getTexCoordGeneration();
            TextureAttributes textureAttributes = appearance.getTextureAttributes();
            if (textureAttributes != null) {
              textureAttributes.getTextureTransform(textureTransform);
            }
            ComparableAppearance comparableAppearance = new ComparableAppearance(appearance);
            String appearanceName = this.appearances.get(comparableAppearance);
            if (appearanceName == null) {
              // Store appearance
              try {
                appearanceName = appearance.getName();
              } catch (NoSuchMethodError ex) {
                // Don't reuse appearance name with Java 3D < 1.4 where getName was added
              }
              if (appearanceName == null || !accept(appearanceName)) {
                appearanceName = objectName;
              } else {
                // Find a unique appearance name among appearances
                Collection<String> appearanceNames = this.appearances.values();
                String baseName = appearanceName + "_" + objectName;
                for (int i = 0; appearanceNames.contains(appearanceName); i++) {
                  if (i == 0) {
                    appearanceName = baseName;
                  } else {
                    appearanceName = baseName + "_" + i;
                  }
                }
              }
              this.appearances.put(comparableAppearance, appearanceName);

              Texture texture = appearance.getTexture();
              if (texture != null) {
                File textureFile = this.textures.get(texture);
                if (textureFile == null) {
                  String fileExtension = "png";
                  URL textureUrl = (URL)texture.getUserData();
                  if (textureUrl instanceof URL) {
                    InputStream in = null;
                    try {
                      // Find the format of the texture image
                      in = openStream(textureUrl);
                      ImageInputStream imageIn = ImageIO.createImageInputStream(in);
                      Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageIn);
                      if (imageReaders.hasNext()) {
                        ImageReader reader = (ImageReader)imageReaders.next();
                        fileExtension = reader.getFormatName().toLowerCase();
                        // Store the URL to copy its image content directly when appearances are saved
                        this.copiedTextures.add(textureUrl);
                      }
                    } catch (IOException ex) {
                      if (in != null) {
                        in.close();
                      }
                    }
                  }

                  // Find a unique texture file name which is not case sensitive
                  String textureFileBaseName = this.mtlFileName.substring(0, this.mtlFileName.length() - 4)
                      + "_" + appearanceName;
                  Collection<File> textureFiles = this.textures.values();
                  boolean fileExists = true;
                  for (int i = 0; fileExists; i++) {
                    if (i == 0) {
                      textureFile = new File(textureFileBaseName + "." + fileExtension);
                    } else {
                      textureFile = new File(textureFileBaseName + "_" + i + "." + fileExtension);
                    }

                    fileExists = false;
                    for (File file : textureFiles) {
                      if (textureFile.getName().equalsIgnoreCase(file.getName())) {
                        fileExists = true;
                        break;
                      }
                    }
                  }
                  // Store texture
                  this.textures.put(texture, textureFile);
                }
              }
            }
            this.out.write("usemtl " + appearanceName + "\n");
          }
        }

        int cullFace = PolygonAttributes.CULL_BACK;
        boolean backFaceNormalFlip = false;
        if (appearance != null) {
          PolygonAttributes polygonAttributes = appearance.getPolygonAttributes();
          if (polygonAttributes != null) {
            cullFace = polygonAttributes.getCullFace();
            backFaceNormalFlip = polygonAttributes.getBackFaceNormalFlip();
          }
        }

        // Write object geometries
        for (int i = 0, n = shape.numGeometries(); i < n; i++) {
          writeNodeGeometry(shape.getGeometry(i), parentTransformations, texCoordGeneration,
              textureTransform, cullFace, backFaceNormalFlip);
        }
      }
    }
  }

  /**
   * Returns an input stream to read the given URL.
   */
  private InputStream openStream(URL url) throws IOException {
    URLConnection connection = url.openConnection();
    if (System.getProperty("os.name").startsWith("Windows")
        && (connection instanceof JarURLConnection)) {
      JarURLConnection urlConnection = (JarURLConnection)connection;
      URL jarFileUrl = urlConnection.getJarFileURL();
      if (jarFileUrl.getProtocol().equalsIgnoreCase("file")) {
        try {
          File file;
          try {
            file = new File(jarFileUrl.toURI());
          } catch (IllegalArgumentException ex) {
            // Try a second way to be able to access to files on Windows servers
            file = new File(jarFileUrl.getPath());
          }
          if (file.canWrite()) {
            // Refuse to use caches to be able to delete the writable files accessed with jar protocol under Windows,
            // as suggested in http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6962459
            connection.setUseCaches(false);
          }
        } catch (URISyntaxException ex) {
          IOException ex2 = new IOException();
          ex2.initCause(ex);
          throw ex2;
        }
      }
    }
    return connection.getInputStream();
  }

  /**
   * Returns <code>true</code> if <code>name</code> contains
   * only letters, digits and underscores.
   */
  private boolean accept(String name) {
    if (name == null) {
      return false;
    }
    for (int i = 0; i < name.length(); i++) {
      char car = name.charAt(i);
      if (!(car >= 'a' && car <= 'z'
            || car >= 'A' && car <= 'Z'
            || car >= '0' && car <= '9'
            || car == '_')) {
        return false;
      }
    }
    return true;
  }

  /**
   * Writes a 3D geometry at OBJ format.
   */
  private void writeNodeGeometry(Geometry geometry,
		  Transform3D parentTransformations,
		  TexCoordGeneration texCoordGeneration,
		  Transform3D textureTransform,
		  int cullFace,
		  boolean backFaceNormalFlip) throws IOException {
	  if (geometry instanceof GeometryArray) {
		  GeometryArray geometryArray = (GeometryArray)geometry;

		  int [] vertexIndexSubstitutes = new int [geometryArray.getVertexCount()];

		  boolean normalsDefined = (geometryArray.getVertexFormat() & GeometryArray.NORMALS) != 0;
		  StringBuilder normalsBuffer;
		  List<Vector3f> addedNormals;
		  if (normalsDefined) {
			  normalsBuffer = new StringBuilder(geometryArray.getVertexCount() * 3 * 10);
			  addedNormals = new ArrayList<Vector3f>();
		  } else {
			  normalsBuffer = null;
			  addedNormals = null;
		  }
		  int [] normalIndexSubstitutes = new int [geometryArray.getVertexCount()];
		  int [] oppositeSideNormalIndexSubstitutes;
		  if (cullFace == PolygonAttributes.CULL_NONE) {
			  oppositeSideNormalIndexSubstitutes = new int [geometryArray.getVertexCount()];
		  } else {
			  oppositeSideNormalIndexSubstitutes = null;
		  }

		  boolean textureCoordinatesDefined = (geometryArray.getVertexFormat() & GeometryArray.TEXTURE_COORDINATE_2) != 0;
		  int [] textureCoordinatesIndexSubstitutes = new int [geometryArray.getVertexCount()];

		  boolean textureCoordinatesGenerated = false;
		  Vector4f planeS = null;
		  Vector4f planeT = null;
		  if (texCoordGeneration != null) {
			  textureCoordinatesGenerated = texCoordGeneration.getGenMode() == TexCoordGeneration.OBJECT_LINEAR
					  && texCoordGeneration.getEnable()
					  && !(geometryArray instanceof IndexedLineArray)
					  && !(geometryArray instanceof IndexedLineStripArray)
					  && !(geometryArray instanceof LineArray)
					  && !(geometryArray instanceof LineStripArray);
			  if (textureCoordinatesGenerated) {
				  planeS = new Vector4f();
				  planeT = new Vector4f();
				  texCoordGeneration.getPlaneS(planeS);
				  texCoordGeneration.getPlaneT(planeT);
			  }
		  }

		  checkCurrentThreadIsntInterrupted();

		  if ((geometryArray.getVertexFormat() & GeometryArray.BY_REFERENCE) != 0) {
			  if ((geometryArray.getVertexFormat() & GeometryArray.INTERLEAVED) != 0) {
				  float [] vertexData = geometryArray.getInterleavedVertices();
				  int vertexSize = vertexData.length / geometryArray.getVertexCount();
				  // Write vertices coordinates
				  for (int index = 0, i = vertexSize - 3, n = geometryArray.getVertexCount();
						  index < n; index++, i += vertexSize) {
					  Point3f vertex = new Point3f(vertexData [i], vertexData [i + 1], vertexData [i + 2]);
					  writeVertex(parentTransformations, vertex, index, vertexIndexSubstitutes);
				  }
				  // Write texture coordinates
				  if (texCoordGeneration != null) {
					  if (textureCoordinatesGenerated) {
						  for (int index = 0, i = vertexSize - 3, n = geometryArray.getVertexCount();
								  index < n; index++, i += vertexSize) {
							  TexCoord2f textureCoordinates = generateTextureCoordinates(
									  vertexData [i], vertexData [i + 1], vertexData [i + 2], planeS, planeT);
							  writeTextureCoordinates(textureCoordinates, textureTransform, index, textureCoordinatesIndexSubstitutes);
						  }
					  }
				  } else if (textureCoordinatesDefined) {
					  for (int index = 0, i = 0, n = geometryArray.getVertexCount();
							  index < n; index++, i += vertexSize) {
						  TexCoord2f textureCoordinates = new TexCoord2f(vertexData [i], vertexData [i + 1]);
						  writeTextureCoordinates(textureCoordinates, textureTransform, index, textureCoordinatesIndexSubstitutes);
					  }
				  }
				  // Write normals
				  if (normalsDefined) {
					  for (int index = 0, i = vertexSize - 6, n = geometryArray.getVertexCount();
							  normalsDefined && index < n; index++, i += vertexSize) {
						  Vector3f normal = new Vector3f(vertexData [i], vertexData [i + 1], vertexData [i + 2]);
						  normalsDefined = writeNormal(normalsBuffer, parentTransformations, normal, index, normalIndexSubstitutes,
								  oppositeSideNormalIndexSubstitutes, addedNormals, cullFace, backFaceNormalFlip);
					  }
				  }
			  } else {
				  // Write vertices coordinates
				  float [] vertexCoordinates = geometryArray.getCoordRefFloat();
				  for (int index = 0, i = 0, n = geometryArray.getVertexCount(); index < n; index++, i += 3) {
					  Point3f vertex = new Point3f(vertexCoordinates [i], vertexCoordinates [i + 1], vertexCoordinates [i + 2]);
					  writeVertex(parentTransformations, vertex, index,
							  vertexIndexSubstitutes);
				  }
				  // Write texture coordinates
				  if (texCoordGeneration != null) {
					  if (textureCoordinatesGenerated) {
						  for (int index = 0, i = 0, n = geometryArray.getVertexCount(); index < n; index++, i += 3) {
							  TexCoord2f textureCoordinates = generateTextureCoordinates(
									  vertexCoordinates [i], vertexCoordinates [i + 1], vertexCoordinates [i + 2], planeS, planeT);
							  writeTextureCoordinates(textureCoordinates, textureTransform, index, textureCoordinatesIndexSubstitutes);
						  }
					  }
				  } else if (textureCoordinatesDefined) {
					  float [] textureCoordinatesArray = geometryArray.getTexCoordRefFloat(0);
					  for (int index = 0, i = 0, n = geometryArray.getVertexCount(); index < n; index++, i += 2) {
						  TexCoord2f textureCoordinates = new TexCoord2f(textureCoordinatesArray [i], textureCoordinatesArray [i + 1]);
						  writeTextureCoordinates(textureCoordinates, textureTransform, index, textureCoordinatesIndexSubstitutes);
					  }
				  }
				  // Write normals
				  if (normalsDefined) {
					  float [] normalCoordinates = geometryArray.getNormalRefFloat();
					  for (int index = 0, i = 0, n = geometryArray.getVertexCount(); normalsDefined && index < n; index++, i += 3) {
						  Vector3f normal = new Vector3f(normalCoordinates [i], normalCoordinates [i + 1], normalCoordinates [i + 2]);
						  normalsDefined = writeNormal(normalsBuffer, parentTransformations, normal, index, normalIndexSubstitutes,
								  oppositeSideNormalIndexSubstitutes, addedNormals, cullFace, backFaceNormalFlip);
					  }
				  }
			  }
		  } else {
			  // Write vertices coordinates
			  for (int index = 0, n = geometryArray.getVertexCount(); index < n; index++) {
				  Point3f vertex = new Point3f();
				  geometryArray.getCoordinate(index, vertex);
				  writeVertex(parentTransformations, vertex, index,
						  vertexIndexSubstitutes);
			  }
			  // Write texture coordinates
			  if (texCoordGeneration != null) {
				  if (textureCoordinatesGenerated) {
					  for (int index = 0, n = geometryArray.getVertexCount(); index < n; index++) {
						  Point3f vertex = new Point3f();
						  geometryArray.getCoordinate(index, vertex);
						  TexCoord2f textureCoordinates = generateTextureCoordinates(
								  vertex.x, vertex.y, vertex.z, planeS, planeT);
						  writeTextureCoordinates(textureCoordinates, textureTransform, index, textureCoordinatesIndexSubstitutes);
					  }
				  }
			  } else if (textureCoordinatesDefined) {
				  for (int index = 0, n = geometryArray.getVertexCount(); index < n; index++) {
					  TexCoord2f textureCoordinates = new TexCoord2f();
					  geometryArray.getTextureCoordinate(0, index, textureCoordinates);
					  writeTextureCoordinates(textureCoordinates, textureTransform, index, textureCoordinatesIndexSubstitutes);
				  }
			  }
			  // Write normals
			  if (normalsDefined) {
				  for (int index = 0, n = geometryArray.getVertexCount(); normalsDefined && index < n; index++) {
					  Vector3f normal = new Vector3f();
					  geometryArray.getNormal(index, normal);
					  normalsDefined = writeNormal(normalsBuffer, parentTransformations, normal, index, normalIndexSubstitutes,
							  oppositeSideNormalIndexSubstitutes, addedNormals, cullFace, backFaceNormalFlip);
				  }
			  }
		  }

		  if (normalsDefined) {
			  // Write normals only if they all contain valid values
			  out.write(normalsBuffer.toString());
		  } else if (addedNormals != null) {
			  // Remove ignored normals
			  for (Vector3f normal : addedNormals) {
				  this.normalIndices.remove(normal);
			  }
		  }

		  checkCurrentThreadIsntInterrupted();

		  // Write lines, triangles or quadrilaterals depending on the geometry
		  if (geometryArray instanceof IndexedGeometryArray) {
			  if (geometryArray instanceof IndexedLineArray) {
				  IndexedLineArray lineArray = (IndexedLineArray)geometryArray;
				  for (int i = 0, n = lineArray.getIndexCount(); i < n; i += 2) {
					  writeIndexedLine(lineArray, i, i + 1, vertexIndexSubstitutes, textureCoordinatesIndexSubstitutes);
				  }
			  } else if (geometryArray instanceof IndexedTriangleArray) {
				  IndexedTriangleArray triangleArray = (IndexedTriangleArray)geometryArray;
				  for (int i = 0, n = triangleArray.getIndexCount(); i < n; i += 3) {
					  writeIndexedTriangle(triangleArray, i, i + 1, i + 2,
							  vertexIndexSubstitutes, normalIndexSubstitutes, oppositeSideNormalIndexSubstitutes,
							  normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, cullFace);
				  }
			  } else if (geometryArray instanceof IndexedQuadArray) {
				  IndexedQuadArray quadArray = (IndexedQuadArray)geometryArray;
				  for (int i = 0, n = quadArray.getIndexCount(); i < n; i += 4) {
					  writeIndexedQuadrilateral(quadArray, i, i + 1, i + 2, i + 3,
							  vertexIndexSubstitutes, normalIndexSubstitutes, oppositeSideNormalIndexSubstitutes,
							  normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, cullFace);
				  }
			  } else if (geometryArray instanceof IndexedGeometryStripArray) {
				  IndexedGeometryStripArray geometryStripArray = (IndexedGeometryStripArray)geometryArray;
				  int [] stripIndexCounts = new int [geometryStripArray.getNumStrips()];
				  geometryStripArray.getStripIndexCounts(stripIndexCounts);
				  int initialIndex = 0;

				  if (geometryStripArray instanceof IndexedLineStripArray) {
					  for (int strip = 0; strip < stripIndexCounts.length; strip++) {
						  for (int i = initialIndex, n = initialIndex + stripIndexCounts [strip] - 1; i < n; i++) {
							  writeIndexedLine(geometryStripArray, i, i + 1,
									  vertexIndexSubstitutes, textureCoordinatesIndexSubstitutes);
						  }
						  initialIndex += stripIndexCounts [strip];
					  }
				  } else if (geometryStripArray instanceof IndexedTriangleStripArray) {
					  for (int strip = 0; strip < stripIndexCounts.length; strip++) {
						  for (int i = initialIndex, n = initialIndex + stripIndexCounts [strip] - 2, j = 0; i < n; i++, j++) {
							  if (j % 2 == 0) {
								  writeIndexedTriangle(geometryStripArray, i, i + 1, i + 2,
										  vertexIndexSubstitutes, normalIndexSubstitutes, oppositeSideNormalIndexSubstitutes,
										  normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, cullFace);
							  } else { // Vertices of odd triangles are in reverse order
								  writeIndexedTriangle(geometryStripArray, i, i + 2, i + 1,
										  vertexIndexSubstitutes, normalIndexSubstitutes, oppositeSideNormalIndexSubstitutes,
										  normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, cullFace);
							  }
						  }
						  initialIndex += stripIndexCounts [strip];
					  }
				  } else if (geometryStripArray instanceof IndexedTriangleFanArray) {
					  for (int strip = 0; strip < stripIndexCounts.length; strip++) {
						  for (int i = initialIndex, n = initialIndex + stripIndexCounts [strip] - 2; i < n; i++) {
							  writeIndexedTriangle(geometryStripArray, initialIndex, i + 1, i + 2,
									  vertexIndexSubstitutes, normalIndexSubstitutes, oppositeSideNormalIndexSubstitutes,
									  normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, cullFace);
						  }
						  initialIndex += stripIndexCounts [strip];
					  }
				  }
			  }
		  } else {
			  if (geometryArray instanceof LineArray) {
				  LineArray lineArray = (LineArray)geometryArray;
				  for (int i = 0, n = lineArray.getVertexCount(); i < n; i += 2) {
					  writeLine(lineArray, i, i + 1, vertexIndexSubstitutes, textureCoordinatesIndexSubstitutes);
				  }
			  } else if (geometryArray instanceof TriangleArray) {
				  TriangleArray triangleArray = (TriangleArray)geometryArray;
				  for (int i = 0, n = triangleArray.getVertexCount(); i < n; i += 3) {
					  writeTriangle(triangleArray, i, i + 1, i + 2,
							  vertexIndexSubstitutes, normalIndexSubstitutes, oppositeSideNormalIndexSubstitutes,
							  normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, cullFace);
				  }
			  } else if (geometryArray instanceof QuadArray) {
				  QuadArray quadArray = (QuadArray)geometryArray;
				  for (int i = 0, n = quadArray.getVertexCount(); i < n; i += 4) {
					  writeQuadrilateral(quadArray, i, i + 1, i + 2, i + 3,
							  vertexIndexSubstitutes, normalIndexSubstitutes, oppositeSideNormalIndexSubstitutes,
							  normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, cullFace);
				  }
			  } else if (geometryArray instanceof GeometryStripArray) {
				  GeometryStripArray geometryStripArray = (GeometryStripArray)geometryArray;
				  int [] stripVertexCounts = new int [geometryStripArray.getNumStrips()];
				  geometryStripArray.getStripVertexCounts(stripVertexCounts);
				  int initialIndex = 0;

				  if (geometryStripArray instanceof LineStripArray) {
					  for (int strip = 0; strip < stripVertexCounts.length; strip++) {
						  for (int i = initialIndex, n = initialIndex + stripVertexCounts [strip] - 1; i < n; i++) {
							  writeLine(geometryStripArray, i, i + 1, vertexIndexSubstitutes, textureCoordinatesIndexSubstitutes);
						  }
						  initialIndex += stripVertexCounts [strip];
					  }
				  } else if (geometryStripArray instanceof TriangleStripArray) {
					  for (int strip = 0; strip < stripVertexCounts.length; strip++) {
						  for (int i = initialIndex, n = initialIndex + stripVertexCounts [strip] - 2, j = 0; i < n; i++, j++) {
							  if (j % 2 == 0) {
								  writeTriangle(geometryStripArray, i, i + 1, i + 2,
										  vertexIndexSubstitutes, normalIndexSubstitutes, oppositeSideNormalIndexSubstitutes,
										  normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, cullFace);
							  } else { // Vertices of odd triangles are in reverse order
								  writeTriangle(geometryStripArray, i, i + 2, i + 1,
										  vertexIndexSubstitutes, normalIndexSubstitutes, oppositeSideNormalIndexSubstitutes,
										  normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, cullFace);
							  }
						  }
						  initialIndex += stripVertexCounts [strip];
					  }
				  } else if (geometryStripArray instanceof TriangleFanArray) {
					  for (int strip = 0; strip < stripVertexCounts.length; strip++) {
						  for (int i = initialIndex, n = initialIndex + stripVertexCounts [strip] - 2; i < n; i++) {
							  writeTriangle(geometryStripArray, initialIndex, i + 1, i + 2,
									  vertexIndexSubstitutes, normalIndexSubstitutes, oppositeSideNormalIndexSubstitutes,
									  normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, cullFace);
						  }
						  initialIndex += stripVertexCounts [strip];
					  }
				  }
			  }
		  }
	  }
  }

  /**
   * Returns texture coordinates generated with <code>texCoordGeneration</code> computed
   * as described in <code>TexCoordGeneration</code> javadoc.
   */
  private TexCoord2f generateTextureCoordinates(float x, float y, float z,
                                                Vector4f planeS,
                                                Vector4f planeT) {
    return new TexCoord2f(x * planeS.x + y * planeS.y + z * planeS.z + planeS.w,
        x * planeT.x + y * planeT.y + z * planeT.z + planeT.w);
  }

  /**
   * Applies to <code>vertex</code> the given transformation, and writes it in
   * a line v at OBJ format, if the vertex wasn't written yet.
   */
  private void writeVertex(Transform3D transformationToParent,
                           Point3f vertex, int index,
                           int [] vertexIndexSubstitutes) throws IOException {
    transformationToParent.transform(vertex);
    Integer vertexIndex = this.vertexIndices.get(vertex);
    if (vertexIndex == null) {
      vertexIndexSubstitutes [index] = this.vertexIndices.size() + 1;
      this.vertexIndices.put(vertex, vertexIndexSubstitutes [index]);
      // Write only once unique vertices
      this.out.write("v " + format(vertex.x)
          + " " + format(vertex.y)
          + " " + format(vertex.z) + "\n");
    } else {
      vertexIndexSubstitutes [index] = vertexIndex;
    }
  }

  /**
   * Formats a float number to a string as fast as possible depending on the
   * format chosen in constructor.
   */
  private String format(float number) {
    if (this.numberFormat != null) {
      return this.numberFormat.format(number);
    } else {
      String numberString = String.valueOf((float)number);
      if (numberString.indexOf('E') != -1) {
        // Avoid scientific notation
        return this.defaultNumberFormat.format(number);
      } else {
        return numberString;
      }
    }
  }

  /**
   * Applies to <code>normal</code> the given transformation, and appends to <code>normalsBuffer</code>
   * its values in a line vn at OBJ format, if the normal wasn't written yet.
   * @return <code>true</code> if the written normal doens't contain any NaN value
   */
  private boolean writeNormal(StringBuilder normalsBuffer,
                              Transform3D transformationToParent,
                              Vector3f normal, int index,
                              int [] normalIndexSubstitutes,
                              int [] oppositeSideNormalIndexSubstitutes,
                              List<Vector3f> addedNormals,
                              int cullFace, boolean backFaceNormalFlip) throws IOException {
    if (Float.isNaN(normal.x) || Float.isNaN(normal.y) || Float.isNaN(normal.z)) {
      return false;
    }
    if (backFaceNormalFlip) {
      normal.negate();
    }
    if (normal.x != 0 || normal.y != 0 || normal.z != 0) {
      transformationToParent.transform(normal);
      normal.normalize();
    }
    Integer normalIndex = this.normalIndices.get(normal);
    if (normalIndex == null) {
      normalIndexSubstitutes [index] = this.normalIndices.size() + 1;
      this.normalIndices.put(normal, normalIndexSubstitutes [index]);
      addedNormals.add(normal);
      // Write only once unique normals
      normalsBuffer.append("vn " + format(normal.x)
          + " " + format(normal.y)
          + " " + format(normal.z) + "\n");
    } else {
      normalIndexSubstitutes [index] = normalIndex;
    }

    if (cullFace == PolygonAttributes.CULL_NONE) {
      Vector3f oppositeNormal = new Vector3f();
      oppositeNormal.negate(normal);
      // Fill opposite side normal index substitutes array
      return writeNormal(normalsBuffer, transformationToParent, oppositeNormal, index, oppositeSideNormalIndexSubstitutes,
          null, addedNormals, PolygonAttributes.CULL_FRONT, false);
    } else {
      return true;
    }
  }

  /**
   * Writes <code>textureCoordinates</code> in a line vt at OBJ format,
   * if the texture coordinates wasn't written yet.
   */
  private void writeTextureCoordinates(TexCoord2f textureCoordinates, Transform3D textureTransform,
                                       int index, int [] textureCoordinatesIndexSubstitutes) throws IOException {
    if (textureTransform.getBestType() != Transform3D.IDENTITY) {
      Point3f transformedCoordinates = new Point3f(textureCoordinates.x, textureCoordinates.y, 0);
      textureTransform.transform(transformedCoordinates);
      textureCoordinates = new TexCoord2f(transformedCoordinates.x, transformedCoordinates.y);
    }
    Integer textureCoordinatesIndex = this.textureCoordinatesIndices.get(textureCoordinates);
    if (textureCoordinatesIndex == null) {
      textureCoordinatesIndexSubstitutes [index] = this.textureCoordinatesIndices.size() + 1;
      this.textureCoordinatesIndices.put(textureCoordinates, textureCoordinatesIndexSubstitutes [index]);
      // Write only once unique texture coordinates
      this.out.write("vt " + format(textureCoordinates.x)
          + " " + format(textureCoordinates.y) + " 0\n");
    } else {
      textureCoordinatesIndexSubstitutes [index] = textureCoordinatesIndex;
    }
  }

  /**
   * Writes the line indices given at vertexIndex1, vertexIndex2,
   * in a line l at OBJ format.
   */
  private void writeIndexedLine(IndexedGeometryArray geometryArray,
                                int vertexIndex1, int vertexIndex2,
                                int [] vertexIndexSubstitutes,
                                int [] textureCoordinatesIndexSubstitutes) throws IOException {
    if ((geometryArray.getVertexFormat() & GeometryArray.TEXTURE_COORDINATE_2) != 0) {
      this.out.write("l " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
          + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex1)])
          + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
          + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex2)]) + "\n");
    } else {
      this.out.write("l " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
          + " "  + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)]) + "\n");
    }
  }

  /**
   * Writes the triangle indices given at vertexIndex1, vertexIndex2, vertexIndex3,
   * in a line f at OBJ format.
   */
  private void writeIndexedTriangle(IndexedGeometryArray geometryArray,
                                    int vertexIndex1, int vertexIndex2, int vertexIndex3,
                                    int [] vertexIndexSubstitutes,
                                    int [] normalIndexSubstitutes,
                                    int [] oppositeSideNormalIndexSubstitutes,
                                    boolean normalsDefined,
                                    int [] textureCoordinatesIndexSubstitutes,
                                    boolean textureCoordinatesGenerated, int cullFace) throws IOException {
    if (cullFace == PolygonAttributes.CULL_FRONT) {
      // Reverse vertex order
      int tmp = vertexIndex1;
      vertexIndex1 = vertexIndex3;
      vertexIndex3 = tmp;
    }

    if (textureCoordinatesGenerated) {
      if (normalsDefined) {
        this.out.write("f " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex1)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex2)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex3)]) + "\n");
      } else {
        this.out.write("f " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)]) + "\n");
      }
    } else if ((geometryArray.getVertexFormat() & GeometryArray.TEXTURE_COORDINATE_2) != 0) {
      if (normalsDefined) {
        this.out.write("f " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex1)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex1)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex2)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex2)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex3)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex3)]) + "\n");
      } else {
        this.out.write("f " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex1)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex2)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex3)]) + "\n");
      }
    } else {
      if (normalsDefined) {
        this.out.write("f " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + "//" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex1)])
            + " "  + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + "//" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex2)])
            + " "  + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + "//" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex3)]) + "\n");
      } else {
        this.out.write("f " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + " "  + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + " "  + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)]) + "\n");
      }
    }

    if (cullFace == PolygonAttributes.CULL_NONE) {
      // Use opposite side normal index substitutes array
      writeIndexedTriangle(geometryArray, vertexIndex1, vertexIndex2, vertexIndex3,
          vertexIndexSubstitutes, oppositeSideNormalIndexSubstitutes, null,
          normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, PolygonAttributes.CULL_FRONT);
    }
  }

  /**
   * Writes the quadrilateral indices given at vertexIndex1, vertexIndex2, vertexIndex3, vertexIndex4,
   * in a line f at OBJ format.
   */
  private void writeIndexedQuadrilateral(IndexedGeometryArray geometryArray,
                                         int vertexIndex1, int vertexIndex2, int vertexIndex3, int vertexIndex4,
                                         int [] vertexIndexSubstitutes,
                                         int [] normalIndexSubstitutes,
                                         int [] oppositeSideNormalIndexSubstitutes,
                                         boolean normalsDefined,
                                         int [] textureCoordinatesIndexSubstitutes,
                                         boolean textureCoordinatesGenerated, int cullFace) throws IOException {
    if (cullFace == PolygonAttributes.CULL_FRONT) {
      // Reverse vertex order
      int tmp = vertexIndex2;
      vertexIndex2 = vertexIndex3;
      vertexIndex3 = tmp;
      tmp = vertexIndex1;
      vertexIndex1 = vertexIndex4;
      vertexIndex4 = tmp;
    }

    if (textureCoordinatesGenerated) {
      if (normalsDefined) {
        this.out.write("f " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex1)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex2)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex3)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex4)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex4)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex4)]) + "\n");
      } else {
        this.out.write("f " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex4)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex4)]) + "\n");
      }
    } else if ((geometryArray.getVertexFormat() & GeometryArray.TEXTURE_COORDINATE_2) != 0) {
      if (normalsDefined) {
        this.out.write("f " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex1)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex1)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex2)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex2)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex3)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex3)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex4)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex4)])
            + "/" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex4)]) + "\n");
      } else {
        this.out.write("f " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex1)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex2)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex3)])
            + " " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex4)])
            + "/" + (textureCoordinatesIndexSubstitutes [geometryArray.getTextureCoordinateIndex(0, vertexIndex4)]) + "\n");
      }
    } else {
      if (normalsDefined) {
        this.out.write("f " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + "//" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex1)])
            + " "  + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + "//" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex2)])
            + " "  + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + "//" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex3)])
            + " "  + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex4)])
            + "//" + (normalIndexSubstitutes [geometryArray.getNormalIndex(vertexIndex4)]) + "\n");
      } else {
        this.out.write("f " + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex1)])
            + " "  + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex2)])
            + " "  + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex3)])
            + " "  + (vertexIndexSubstitutes [geometryArray.getCoordinateIndex(vertexIndex4)]) + "\n");
      }
    }

    if (cullFace == PolygonAttributes.CULL_NONE) {
      // Use opposite side normal index substitutes array
      writeIndexedQuadrilateral(geometryArray, vertexIndex1, vertexIndex2, vertexIndex3, vertexIndex4,
          vertexIndexSubstitutes, oppositeSideNormalIndexSubstitutes, null,
          normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, PolygonAttributes.CULL_FRONT);
    }
  }

  /**
   * Writes the line indices given at vertexIndex1, vertexIndex2,
   * in a line l at OBJ format.
   */
  private void writeLine(GeometryArray geometryArray,
                         int vertexIndex1, int vertexIndex2,
                         int [] vertexIndexSubstitutes,
                         int [] textureCoordinatesIndexSubstitutes) throws IOException {
    if ((geometryArray.getVertexFormat() & GeometryArray.TEXTURE_COORDINATE_2) != 0) {
      this.out.write("l " + (vertexIndexSubstitutes [vertexIndex1])
          + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex1])
          + " " + (vertexIndexSubstitutes [vertexIndex2])
          + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex2]) + "\n");
    } else {
      this.out.write("l " + (vertexIndexSubstitutes [vertexIndex1])
          + " "  + (vertexIndexSubstitutes [vertexIndex2]) + "\n");
    }
  }

  /**
   * Writes the triangle indices given at vertexIndex1, vertexIndex2, vertexIndex3,
   * in a line f at OBJ format.
   */
  private void writeTriangle(GeometryArray geometryArray,
                             int vertexIndex1, int vertexIndex2, int vertexIndex3,
                             int [] vertexIndexSubstitutes,
                             int [] normalIndexSubstitutes,
                             int [] oppositeSideNormalIndexSubstitutes,
                             boolean normalsDefined,
                             int [] textureCoordinatesIndexSubstitutes,
                             boolean textureCoordinatesGenerated, int cullFace) throws IOException {
    if (cullFace == PolygonAttributes.CULL_FRONT) {
      // Reverse vertex order
      int tmp = vertexIndex1;
      vertexIndex1 = vertexIndex3;
      vertexIndex3 = tmp;
    }

    if (textureCoordinatesGenerated
        || (geometryArray.getVertexFormat() & GeometryArray.TEXTURE_COORDINATE_2) != 0) {
      if (normalsDefined) {
        this.out.write("f " + (vertexIndexSubstitutes [vertexIndex1])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex1])
            + "/" + (normalIndexSubstitutes [vertexIndex1])
            + " " + (vertexIndexSubstitutes [vertexIndex2])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex2])
            + "/" + (normalIndexSubstitutes [vertexIndex2])
            + " " + (vertexIndexSubstitutes [vertexIndex3])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex3])
            + "/" + (normalIndexSubstitutes [vertexIndex3]) + "\n");
      } else {
        this.out.write("f " + (vertexIndexSubstitutes [vertexIndex1])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex1])
            + " " + (vertexIndexSubstitutes [vertexIndex2])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex2])
            + " " + (vertexIndexSubstitutes [vertexIndex3])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex3]) + "\n");
      }
    } else {
      if (normalsDefined) {
        this.out.write("f " + (vertexIndexSubstitutes [vertexIndex1])
            + "//" + (normalIndexSubstitutes [vertexIndex1])
            + " "  + (vertexIndexSubstitutes [vertexIndex2])
            + "//" + (normalIndexSubstitutes [vertexIndex2])
            + " "  + (vertexIndexSubstitutes [vertexIndex3])
            + "//" + (normalIndexSubstitutes [vertexIndex3]) + "\n");
      } else {
        this.out.write("f " + (vertexIndexSubstitutes [vertexIndex1])
            + " "  + (vertexIndexSubstitutes [vertexIndex2])
            + " "  + (vertexIndexSubstitutes [vertexIndex3]) + "\n");
      }
    }

    if (cullFace == PolygonAttributes.CULL_NONE) {
      // Use opposite side normal index substitutes array
      writeTriangle(geometryArray, vertexIndex1, vertexIndex2, vertexIndex3,
          vertexIndexSubstitutes, oppositeSideNormalIndexSubstitutes, null,
          normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, PolygonAttributes.CULL_FRONT);
    }
  }

  /**
   * Writes the quadrilateral indices given at vertexIndex1, vertexIndex2, vertexIndex3, vertexIndex4,
   * in a line f at OBJ format.
   */
  private void writeQuadrilateral(GeometryArray geometryArray,
                                  int vertexIndex1, int vertexIndex2, int vertexIndex3, int vertexIndex4,
                                  int [] vertexIndexSubstitutes,
                                  int [] normalIndexSubstitutes,
                                  int [] oppositeSideNormalIndexSubstitutes,
                                  boolean normalsDefined,
                                  int [] textureCoordinatesIndexSubstitutes,
                                  boolean textureCoordinatesGenerated, int cullFace) throws IOException {
    if (cullFace == PolygonAttributes.CULL_FRONT) {
      // Reverse vertex order
      int tmp = vertexIndex2;
      vertexIndex2 = vertexIndex3;
      vertexIndex3 = tmp;
      tmp = vertexIndex1;
      vertexIndex1 = vertexIndex4;
      vertexIndex4 = tmp;
    }

    if (textureCoordinatesGenerated
        || (geometryArray.getVertexFormat() & GeometryArray.TEXTURE_COORDINATE_2) != 0) {
      if (normalsDefined) {
        this.out.write("f " + (vertexIndexSubstitutes [vertexIndex1])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex1])
            + "/" + (normalIndexSubstitutes [vertexIndex1])
            + " " + (vertexIndexSubstitutes [vertexIndex2])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex2])
            + "/" + (normalIndexSubstitutes [vertexIndex2])
            + " " + (vertexIndexSubstitutes [vertexIndex3])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex3])
            + "/" + (normalIndexSubstitutes [vertexIndex3])
            + " " + (vertexIndexSubstitutes [vertexIndex4])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex4])
            + "/" + (normalIndexSubstitutes [vertexIndex4]) + "\n");
      } else {
        this.out.write("f " + (vertexIndexSubstitutes [vertexIndex1])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex1])
            + " " + (vertexIndexSubstitutes [vertexIndex2])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex2])
            + " " + (vertexIndexSubstitutes [vertexIndex3])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex3])
            + " " + (vertexIndexSubstitutes [vertexIndex4])
            + "/" + (textureCoordinatesIndexSubstitutes [vertexIndex4]) + "\n");
      }
    } else {
      if (normalsDefined) {
        this.out.write("f " + (vertexIndexSubstitutes [vertexIndex1])
            + "//" + (normalIndexSubstitutes [vertexIndex1])
            + " "  + (vertexIndexSubstitutes [vertexIndex2])
            + "//" + (normalIndexSubstitutes [vertexIndex2])
            + " "  + (vertexIndexSubstitutes [vertexIndex3])
            + "//" + (normalIndexSubstitutes [vertexIndex3])
            + " "  + (vertexIndexSubstitutes [vertexIndex4])
            + "//" + (normalIndexSubstitutes [vertexIndex4]) + "\n");
      } else {
        this.out.write("f " + (vertexIndexSubstitutes [vertexIndex1])
            + " "  + (vertexIndexSubstitutes [vertexIndex2])
            + " "  + (vertexIndexSubstitutes [vertexIndex3])
            + " "  + (vertexIndexSubstitutes [vertexIndex4]) + "\n");
      }
    }

    if (cullFace == PolygonAttributes.CULL_NONE) {
      // Use opposite side normal index substitutes array
      writeQuadrilateral(geometryArray, vertexIndex1, vertexIndex2, vertexIndex3, vertexIndex4,
          vertexIndexSubstitutes, oppositeSideNormalIndexSubstitutes, null,
          normalsDefined, textureCoordinatesIndexSubstitutes, textureCoordinatesGenerated, PolygonAttributes.CULL_FRONT);
    }
  }

  /**
   * Closes this writer and writes MTL file and its texture images,
   * if this writer was created from a file.
   * @throws IOException if this writer couldn't be closed
   *                     or couldn't write MTL and texture files couldn't be written
   * @throws InterruptedIOException if the current thread was interrupted during this operation
   *         The interrupted status of the current thread is cleared when this exception is thrown.
   */
  @Override
  public void close() throws IOException, InterruptedIOException {
    super.close();
    if (this.mtlFileName != null) {
      writeAppearancesToMTLFile();
    }
  }

  /**
   * Exports a set of appearance to a MTL file built from OBJ file name.
   */
  private void writeAppearancesToMTLFile() throws IOException {
    Writer writer = null;
    try {
      writer = new OutputStreamWriter(
          new BufferedOutputStream(new FileOutputStream(this.mtlFileName)), "ISO-8859-1");
      writeHeader(writer);
      for (Map.Entry<ComparableAppearance, String> appearanceEntry : this.appearances.entrySet()) {
        checkCurrentThreadIsntInterrupted();

        Appearance appearance = appearanceEntry.getKey().getAppearance();
        String appearanceName = appearanceEntry.getValue();
        writer.write("\nnewmtl " + appearanceName + "\n");
        Material material = appearance.getMaterial();
        if (material != null) {
          if (material instanceof OBJMaterial
              && ((OBJMaterial)material).isIlluminationModelSet()) {
            writer.write("illum " + ((OBJMaterial)material).getIlluminationModel() + "\n");
          } else if (material.getShininess() > 1) {
            writer.write("illum 2\n");
          } else if (material.getLightingEnable()) {
            writer.write("illum 1\n");
          } else {
            writer.write("illum 0\n");
          }
          Color3f color = new Color3f();
          material.getAmbientColor(color);
          writer.write("Ka " + format(color.x) + " " + format(color.y) + " " + format(color.z) + "\n");
          material.getDiffuseColor(color);
          writer.write("Kd " + format(color.x) + " " + format(color.y) + " " + format(color.z) + "\n");
          material.getSpecularColor(color);
          writer.write("Ks " + format(color.x) + " " + format(color.y) + " " + format(color.z) + "\n");
          writer.write("Ns " + format(material.getShininess()) + "\n");
          if (material instanceof OBJMaterial) {
            OBJMaterial objMaterial = (OBJMaterial)material;
            if (objMaterial.isOpticalDensitySet()) {
              writer.write("Ni " + format(objMaterial.getOpticalDensity()) + "\n");
            }
            if (objMaterial.isSharpnessSet()) {
              writer.write("sharpness " + format(objMaterial.getSharpness()) + "\n");
            }
          }
        } else {
          ColoringAttributes coloringAttributes = appearance.getColoringAttributes();
          if (coloringAttributes != null) {
            writer.write("illum 0\n");
            Color3f color = new Color3f();
            coloringAttributes.getColor(color);
            writer.write("Ka " + format(color.x) + " " + format(color.y) + " " + format(color.z) + "\n");
            writer.write("Kd " + format(color.x) + " " + format(color.y) + " " + format(color.z) + "\n");
            writer.write("Ks " + format(color.x) + " " + format(color.y) + " " + format(color.z) + "\n");
          }
        }
        TransparencyAttributes transparency = appearance.getTransparencyAttributes();
        if (transparency != null) {
          if (!(material instanceof OBJMaterial)) {
            writer.write("Ni 1\n");
          }
          writer.write("d " + format(1f - transparency.getTransparency()) + "\n");
        }
        Texture texture = appearance.getTexture();
        if (texture != null) {
          writer.write("map_Kd " + this.textures.get(texture).getName() + "\n");
        }
      }

      for (Map.Entry<Texture, File> textureEntry : this.textures.entrySet()) {
        Texture texture = textureEntry.getKey();
        Object textureUrl = texture.getUserData();
        if (this.copiedTextures.contains(textureUrl)) {
          // Copy texture image file directly
          InputStream in = null;
          OutputStream out = null;
          try {
            in = openStream((URL)textureUrl);
            out = new FileOutputStream(textureEntry.getValue());
            byte [] buffer = new byte [8192];
            int size;
            while ((size = in.read(buffer)) != -1) {
              out.write(buffer, 0, size);
            }
          } finally {
            if (in != null) {
              in.close();
            }
            if (out != null) {
              out.close();
            }
          }
        } else {
          ImageComponent2D imageComponent = (ImageComponent2D)texture.getImage(0);
          RenderedImage image = imageComponent.getRenderedImage();
          ImageIO.write(image, "png", textureEntry.getValue());
        }
      }
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }

  /**
   * Writes <code>node</code> in an entry at OBJ format of the given zip file
   * along with its MTL file and texture images.
   */
  public static void writeNodeInZIPFile(Node node,
                                        File zipFile,
                                        int compressionLevel,
                                        String entryName,
                                        String header) throws IOException {
    writeNodeInZIPFile(node, null, zipFile, compressionLevel, entryName, header);
  }

  /**
   * Writes <code>node</code> in an entry at OBJ format of the given zip file
   * along with its MTL file and texture images.
   * Once saved, <code>materialAppearances</code> will contain the appearances matching
   * each material saved in the MTL file. Material names used as keys maybe be different
   * of the appearance names to respect MTL specifications.
   */
  public static void writeNodeInZIPFile(Node node,
                                        Map<String, Appearance> materialAppearances,
                                        File zipFile,
                                        int compressionLevel,
                                        String entryName,
                                        String header) throws IOException {
    // Create a temporary folder
    File tempFolder = null;
    for (int i = 0; i < 10 && tempFolder == null; i++) {
      tempFolder = File.createTempFile("obj", "tmp");
      tempFolder.delete();
      if (!tempFolder.mkdirs()) {
        tempFolder = null;
      }
    }
    if (tempFolder == null) {
      throw new IOException("Couldn't create a temporary folder");
    }
    
    
    
    ZipOutputStream zipOut = null;
    try {
      // Write model in an OBJ file
      OBJWriter writer = new OBJWriter(new File(tempFolder, entryName), header, -1);
      writer.writeNode(node);

      Writer jsonwriter;
      jsonwriter = new OutputStreamWriter(
              new BufferedOutputStream(new FileOutputStream(writer.jsonFileName)), "ISO-8859-1");
      String finaljson = "";
      if (writer.objects.lastIndexOf(",") > 0) {
    	  finaljson = writer.objects.substring(0, writer.objects.lastIndexOf(","));
      
      }
      finaljson += "\n}\n";
      jsonwriter.write(finaljson);
      writer.close();
      jsonwriter.close();
      // Create a ZIP file containing temp folder files (OBJ + MTL + texture files)
      zipOut = new ZipOutputStream(new FileOutputStream(zipFile));
      zipOut.setLevel(compressionLevel);
      for (File tempFile : tempFolder.listFiles()) {
        if (tempFile.isFile()) {
          InputStream tempIn = null;
          try {
            zipOut.putNextEntry(new ZipEntry(tempFile.getName()));
            tempIn = new FileInputStream(tempFile);
            byte [] buffer = new byte [8096];
            int size;
            while ((size = tempIn.read(buffer)) != -1) {
              zipOut.write(buffer, 0, size);
            }
            zipOut.closeEntry();
          } finally {
            if (tempIn != null) {
              tempIn.close();
            }
          }
        }
      }

      if (materialAppearances != null) {
        // Update material / appearance map
        for (Map.Entry<ComparableAppearance, String> appearanceEntry : writer.appearances.entrySet()) {
          materialAppearances.put(appearanceEntry.getValue(), appearanceEntry.getKey().getAppearance());
        }
      }
    } finally {
      if (zipOut != null) {
        zipOut.close();
      }
      // Empty tempFolder
      for (File tempFile : tempFolder.listFiles()) {
        if (tempFile.isFile()) {
          tempFile.delete();
        }
      }
      tempFolder.delete();
    }
  }


  /**
   * An <code>Appearance</code> wrapper able to compare
   * if two appearances are equal for MTL format.
   */
  private static class ComparableAppearance {
    private Appearance appearance;

    public ComparableAppearance(Appearance appearance) {
      this.appearance = appearance;
    }

    public Appearance getAppearance() {
      return this.appearance;
    }

    /**
     * Returns <code>true</code> if this appearance and the one of <code>obj</code>
     * describe the same colors, transparency and texture.
     */
    @Override
    public boolean equals(Object obj) {
      if (obj instanceof ComparableAppearance) {
        Appearance appearance2 = ((ComparableAppearance)obj).appearance;
        // Compare coloring attributes
        ColoringAttributes coloringAttributes1 = this.appearance.getColoringAttributes();
        ColoringAttributes coloringAttributes2 = appearance2.getColoringAttributes();
        if ((coloringAttributes1 == null) ^ (coloringAttributes2 == null)) {
          return false;
        } else if (coloringAttributes1 != coloringAttributes2) {
          Color3f color1 = new Color3f();
          Color3f color2 = new Color3f();
          coloringAttributes1.getColor(color1);
          coloringAttributes2.getColor(color2);
          if (!color1.equals(color2)) {
            return false;
          }
        }
        // Compare material colors
        Material material1 = this.appearance.getMaterial();
        Material material2 = appearance2.getMaterial();
        if ((material1 == null) ^ (material2 == null)) {
          return false;
        } else if (material1 != material2) {
          Color3f color1 = new Color3f();
          Color3f color2 = new Color3f();
          material1.getAmbientColor(color1);
          material2.getAmbientColor(color2);
          if (!color1.equals(color2)) {
            return false;
          } else {
            material1.getDiffuseColor(color1);
            material2.getDiffuseColor(color2);
            if (!color1.equals(color2)) {
              return false;
            } else {
              material1.getEmissiveColor(color1);
              material2.getEmissiveColor(color2);
              if (!color1.equals(color2)) {
                return false;
              } else {
                material1.getSpecularColor(color1);
                material2.getSpecularColor(color2);
                if (!color1.equals(color2)) {
                  return false;
                } else if (material1.getShininess() != material2.getShininess()) {
                  return false;
                } else if (material1.getClass() != material2.getClass()) {
                  return false;
                } else if (material1.getClass() == OBJMaterial.class) {
                  OBJMaterial objMaterial1 = (OBJMaterial)material1;
                  OBJMaterial objMaterial2 = (OBJMaterial)material2;
                  if (objMaterial1.isOpticalDensitySet() ^ objMaterial2.isOpticalDensitySet()) {
                    return false;
                  } else if (objMaterial1.isOpticalDensitySet() && objMaterial2.isOpticalDensitySet()
                            && objMaterial1.getOpticalDensity() != objMaterial2.getOpticalDensity()) {
                    return false;
                  } else if (objMaterial1.isIlluminationModelSet() ^ objMaterial2.isIlluminationModelSet()) {
                    return false;
                  } else if (objMaterial1.isIlluminationModelSet() && objMaterial2.isIlluminationModelSet()
                            && objMaterial1.getIlluminationModel() != objMaterial2.getIlluminationModel()) {
                    return false;
                  } else if (objMaterial1.isSharpnessSet() ^ objMaterial2.isSharpnessSet()) {
                    return false;
                  } else if (objMaterial1.isSharpnessSet() && objMaterial2.isSharpnessSet()
                            && objMaterial1.getSharpness() != objMaterial2.getSharpness()) {
                    return false;
                  }
                }
              }
            }
          }
        }
        // Compare transparency
        TransparencyAttributes transparency1 = this.appearance.getTransparencyAttributes();
        TransparencyAttributes transparency2 = appearance2.getTransparencyAttributes();
        if ((transparency1 == null) ^ (transparency2 == null)) {
          return false;
        } else if (transparency1 != transparency2) {
          if (transparency1.getTransparency() != transparency2.getTransparency()) {
            return false;
          }
        }
        // Compare texture
        Texture texture1 = this.appearance.getTexture();
        Texture texture2 = appearance2.getTexture();
        if ((texture1 == null) ^ (texture2 == null)) {
          return false;
        } else if (texture1 != texture2) {
          if (texture1.getImage(0) != texture2.getImage(0)) {
            return false;
          }
        }
        // Compare name
        try {
          String name1 = this.appearance.getName();
          String name2 = appearance2.getName();
          if ((name1 == null) ^ (name2 == null)) {
            return false;
          } else if (name1 != name2
                     && !name1.equals(name2)) {
            return false;
          }
        } catch (NoSuchMethodError ex) {
          // Don't compare name with Java 3D < 1.4 where getName was added
        }

        return true;
      }
      return false;
    }

    @Override
    public int hashCode() {
      int code = 0;
      ColoringAttributes coloringAttributes = appearance.getColoringAttributes();
      if (coloringAttributes != null) {
        Color3f color = new Color3f();
        coloringAttributes.getColor(color);
        code += color.hashCode();
      }
      Material material = this.appearance.getMaterial();
      if (material != null) {
        Color3f color = new Color3f();
        material.getAmbientColor(color);
        code += color.hashCode();
        material.getDiffuseColor(color);
        code += color.hashCode();
        material.getEmissiveColor(color);
        code += color.hashCode();
        material.getSpecularColor(color);
        code += color.hashCode();
        code += Float.floatToIntBits(material.getShininess());
      }
      TransparencyAttributes transparency = this.appearance.getTransparencyAttributes();
      if (transparency != null) {
        code += Float.floatToIntBits(transparency.getTransparency());
      }
      Texture texture = this.appearance.getTexture();
      if (texture != null) {
        code += texture.getImage(0).hashCode();
      }
      try {
        String name = this.appearance.getName();
        if (name != null) {
          code += name.hashCode();
        }
      } catch (NoSuchMethodError ex) {
        // Don't take name into account with Java 3D < 1.4 where getName was added
      }
      return code;
    }
  }
}
