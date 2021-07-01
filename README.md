# ExportToHASS
Sweethome3D Plugin for Home Assistant Export

## Plugion Installation

Copy the file https://github.com/adizanni/ExportToHASS/blob/main/dist/ExportToHassPlugin.sh3p?raw=true to the Plugin Sweethome directory. In linux this is ~/.eteks/sweethome3d/plugins/

## How to use

Open your model, click on menu 'Tools\Export obj to HASS', choose the  name of the zip file where to store the wavefront format (obj, mtl and texture files). Once the zip created unzip it to your homeassistant /config/www/<your model folder> the model will have the 'home' name by default.
  
### How it works
 
I have conceived this plugin to workaround the object_id change happening between 2 exports when other objects are removed from the model. This is achieved by using the object name that you will find in the properties of the 3D object in Sweethome3D. Of course the name of the object should be unique in the model (no control yet, this version is still highly rudimentary)

Give feedback.
