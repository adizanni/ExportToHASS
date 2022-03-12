package com.eteks.sweethome3d.plugin.exporthass;

import com.eteks.sweethome3d.plugin.PluginAction;
import com.eteks.sweethome3d.swing.FileContentManager;
import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.tools.OperatingSystem;
import com.eteks.sweethome3d.viewcontroller.ContentManager;
import com.eteks.sweethome3d.viewcontroller.HomeView;
import com.eteks.sweethome3d.viewcontroller.Object3DFactory;
import com.eteks.sweethome3d.viewcontroller.View;

import org.json.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Group;
import javax.media.j3d.Node;
import javax.media.j3d.Shape3D;
import javax.swing.filechooser.FileFilter;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import com.eteks.sweethome3d.SweetHome3D;
import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.j3d.Ground3D;
import com.eteks.sweethome3d.j3d.Object3DBranchFactory;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.HomeObject;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.Wall;


public class ExportHass extends Plugin {
	public class ExportHassAction extends PluginAction {
		private String resourceBaseName;
		
		public void execute() {
            // TODO Auto-generated method stub
			final HomeView homeView = getHomeController().getView();
			
			ContentManager contentManagerWithZipExtension = new FileContentManager(getUserPreferences()) {
		        private final String ZIP_EXTENSION = ".zip";
		        private final FileFilter ZIP_FILE_FILTER = new FileFilter() {
		          @Override
		          public boolean accept(File file) {
		            // Accept directories and ZIP files
		            return file.isDirectory()
		                || file.getName().toLowerCase().endsWith(ZIP_EXTENSION);
		          }

		          @Override
		          public String getDescription() {
		            return "ZIP";
		          }
		        };

		        @Override
		        public String getDefaultFileExtension(ContentType contentType) {
		          if (contentType == ContentType.USER_DEFINED) {
		            return this.ZIP_EXTENSION;
		          } else {
		            return super.getDefaultFileExtension(contentType);
		          }
		        }

		        @Override
		        protected String [] getFileExtensions(ContentType contentType) {
		          if (contentType == ContentType.USER_DEFINED) {
		            return new String [] {this.ZIP_EXTENSION};
		          } else {
		            return super.getFileExtensions(contentType);
		          }
		        }

		        @Override
		        protected FileFilter [] getFileFilter(ContentType contentType) {
		          if (contentType == ContentType.USER_DEFINED) {
		            return new FileFilter [] {this.ZIP_FILE_FILTER};
		          } else {
		            return super.getFileFilter(contentType);
		          }
		        }
		      };
			
		      final String exportedFile = contentManagerWithZipExtension.showSaveDialog(homeView,
		    		  "Choose a Directory to store zipped obj",
		              ContentManager.ContentType.USER_DEFINED, getHome().getName());
			
    		String homeStructure;
			homeStructure = "HomeStructure/Home.obj";
			File homeStructureFile;
			
			try {
				homeStructureFile = exportHomeStructure(getHome(), new Object3DBranchFactory(),
				    homeStructure.substring(homeStructure.lastIndexOf('/') + 1), new File(exportedFile));
				JOptionPane.showMessageDialog(null, "Model succesfully exported to Home Assistant friendly format", "Ok", JOptionPane.INFORMATION_MESSAGE);
				//homeStructureFile.
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }

		public ExportHassAction() {
	        putPropertyValue(Property.NAME, "Export obj to HASS");
	        putPropertyValue(Property.MENU, "Tools");
	        // Enables the action by default
	        setEnabled(true);
	     }
	
		private File exportHomeStructure(Home home, Object3DFactory objectFactory,
	            String homeStructureObjName, File exportedFile) throws IOException {
			// Clone home to be able to handle it independently
			home = home.clone();
			List<Level> levels = home.getLevels();
			for (int i = 0; i < levels.size(); i++) {
				if (levels.get(i).isViewable()) {
					levels.get(i).setVisible(true);
				}
			}

			BranchGroup root = new BranchGroup();
			// Add 3D ground, walls, rooms and labels
			//root.addChild(new Ground3D(home, -0.5E5f, -0.5E5f, 1E5f, 1E5f, true));
			
		
			Integer wallIndex = 0;
		    Integer roomIndex = 0;	
		    Integer objectIndex = 0;
		    String level = "";
			
		    for (HomeObject item : home.getHomeObjects()) {
		    	//JOptionPane.showMessageDialog(null, ((HomePieceOfFurniture) item).getName(), "Item", JOptionPane.INFORMATION_MESSAGE);
		    	if (item instanceof HomeEnvironment || item instanceof Camera || item instanceof Level) {
		    		continue;
		    	}
		    	if (item instanceof HomeFurnitureGroup) {
	    			
	    			for (HomePieceOfFurniture furniture : ((HomeFurnitureGroup) item).getAllFurniture()) {
	    				Node furniturenode = (Node) objectFactory.createObject3D(home,(Selectable) furniture, true);
	    				if (levels.size() > 1) {
	    					level = String.format("lvl%03d", levels.indexOf(furniture.getLevel()));
	    				} else { 
	    					level = "";
	    				}
	    				furniturenode.setName(level+((HomeFurnitureGroup) item).getName()+"_"+furniture.getName());
	    				root.addChild(furniturenode);
	    				 
	    			}
	    			
	    		} else {
		    	
		    	  Node newnode = (Node) objectFactory.createObject3D(home,(Selectable) item, true);
		    	  if (newnode != null) {
		    		
		    		if (item instanceof HomePieceOfFurniture) {
		    			if (levels.size() > 1) {
		    				level = String.format("lvl%03d", levels.indexOf(((HomePieceOfFurniture) item).getLevel()));
	    				} else { 
	    					level = "";
	    				}
		    			newnode.setName(level+((HomePieceOfFurniture) item).getName());
		    		}
		    		else if (item instanceof Wall) {
		    			if (levels.size() > 1) {
		    				level = String.format("lvl%03d", levels.indexOf(((Wall) item).getLevel()));
			    		} else { 
	    					level = "";
	    				}
		    			newnode.setName(level+"wall_"+ wallIndex++ );
		    		}
		    		else if (item instanceof Room) {
		    			if (levels.size() > 1) {
		    				level = String.format("lvl%03d", levels.indexOf(((Room) item).getLevel()));
			    		} else { 
	    					level = "";
	    				}
		    			newnode.setName(level+"room_"+ roomIndex++ );
		    		}
		    		else {
		    			if (levels.size() > 1) {
		    				level = String.format("lvl%03d", 0);
			    		} else { 
	    					level = "";
	    				}
		    			newnode.setName(level+"object_"+ objectIndex++ );
		    		} 
		    		root.addChild(newnode);
		    	
		    	  }
	    		}
		    	
		    }
			
			OBJWriter.writeNodeInZIPFile(root, exportedFile, 0, "home.obj", "Home structure for HASS export");
			return exportedFile;
		}
	}

	@Override
    public PluginAction[] getActions() {
        // TODO Auto-generated method stub
		return new PluginAction [] {new ExportHassAction()};
    }


	
}


