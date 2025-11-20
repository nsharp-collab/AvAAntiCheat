# AvAAntiCheat
This is an anti-cheat I made for Minecraft java servers, it works on servers that use Bukkit/Spigot API and works in minecraft version 1.21.10
the anti-cheat detects flying, chat spaming, combat logging, auto clicking and some types of packet minipulation. The Anti-Cheat also logs detections even if no action was taken (E.X if it detected someone flying for a small amount of time that would only be there first violation and no action is taken but that will show in the logs)

 # Installing 
 Installing the Anti-Cheat isn't hard and is really simple.
 You can either:
 A. Download the .Jar file on github located in either the target folder
 B. DOwnload the .Jar file from Cursed forge (currently the only site the AC is on)
 C. Clone the repository onto your computer (```git clone https://github.com/nsharp-collab/AvAAntiCheat.git```) or into another Github repository then run ```mvn clean package``` then it will be in the target folder. If it fails please make a issue ticket on the offical Github repository

 After you have the .Jar file, drag it into your servers plugin folder, then run the server and it should auto install. to make sure it installed correctly you can run /ac status

 # Issues
 If you have any issues with the Anti-Cheat, please open an issue on Github

# extras
If you install the Anti-Cheat into your server and it says "version 1.8.8" when running /ac status or when the server is starting even though the name of the .Jar file says "version 1.8.9" or somthing simular you can either compile it yourself (see step C under the Installing section) or open a Github Issue. If you compile it yourself and it says the same thing it means i probobly forgot to change the version in the code
