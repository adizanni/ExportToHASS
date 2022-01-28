# ExportToHASS
Sweethome3D Plugin for Home Assistant Export to be used together with the floor 3D card (https://github.com/adizanni/floor3d-card)

## Plugin Installation

Copy the file https://github.com/adizanni/ExportToHASS/blob/main/dist/ExportToHassPlugin.sh3p?raw=true to the Plugin Sweethome directory. In linux this is the folder ~/.eteks/sweethome3d/plugins/. In Windows just double-click on the sh3p plugin file (I've been reported some issues related to this installation method, please follow [this](https://github.com/adizanni/ExportToHASS/issues/1#issuecomment-904966326) procedure instead)
The plugin binary is currently compiled with SweetHome3d 6.6 and I can only guarantee it working in that version. Starting from this version I will create releases tagged with the SweetHome3d version, so that if you do not want to upgrade your sweethome version, you will stay with the old one that is working for you (of course you will not benefit from the bug fix and new features).

## How to use

Open your model, click on menu 'Tools\Export obj to HASS', choose the  name of the zip file where to store the wavefront format (obj, mtl and texture files). Once the zip created unzip it to your homeassistant /config/www/<your model folder> the model will have the 'home' name by default. You will find also in the zip a json file with a list of object_id of the file, this  is used to feed the object_id dropdown in the card editor of HomeAssistant.
  
### How it works
 
I have conceived this plugin to workaround the object_id change happening between 2 exports when other objects are removed from the model. This is achieved by using the object name that you will find in the properties of the 3D object in Sweethome3D. Of course the name of the object should be unique in the model (no control yet, this version is still highly rudimentary). If you want an object to group together all the components with a unique name you just append a '#' to the end of the object name. 

Give feedback.
