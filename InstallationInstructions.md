# Introduction #

This should describe how to install SRW on your server and how to hook it up to your DSpace database


# Details #

## Step 1: Download the SRW server, build and deploy it ##
1a: in a safe directory:
```
svn checkout http://oclcsrw.googlecode.com/svn/trunk/ oclcsrw-read-only
cd oclcsrw-read-only
ant
```
1b: copy the SRW server to your `<tomcat>`/webapps directory
<br />e.g.
```
cp dist/SRW.war /var/lib/tomcat6/webapps
```
1c: restart the tomcat server
<br />e.g. (from the tomcat6 documentation)
```
sudo /etc/init.d/tomcat6 restart
```
1d: test the SRW server from your browser (you may have to substitute another host and port in the example)
<br />e.g.
```
http://localhost:8080/SRW/search/test
```
That should bring up a simple search screen and any searches should return the same simple document.

## Step 2: Download the DSpace interface, build and deploy it ##
2a: in the same safe directory:
```
svn checkout http://oclcsrwdspacelucene.googlecode.com/svn/trunk/ oclcsrwdspacelucene-read-only
cd oclcsrwdspacelucene-read-only
ant
```
2b: copy the DSpace interface code to the SRW webapp
<br />e.g.
```
cp dist/SRWDSpaceLucene.jar /var/lib/tomcat6/webapps/SRW/WEB-INF/lib
```
2c: copy the DSpace executable jars to the SRW webapp (I have no idea which of those is actually necessary, so I just copy them all)
<br />e.g.
```
cp /dspace/lib/* /var/lib/tomcat6/webapps/SRW/WEB-INF/lib
```

## Step 3: Copy and edit configuration files ##
3a: edit SRWServer.props to expose the DSpace database
```
vi /var/lib/tomcat6/webapps/SRW/WEB-INF/classes/SRWServer.props
# find the group of three lines that begin '#db.DSpace'
# remove the leading '#' to un-comment them
# change the home directory from "d:/dspace/" to the path to your DSpace directory
# (it should have the config directory in it)
```
3b: copy the DSpace.SRWDatabase.props from the oclcsrwdspacelucene-read-only to your dspace/config directory
<br />e.g.
```
cp conf/DSpace.SRWDatabase.props /dspace/config
```

## Step 4: Bounce your tomcat and test your DSpace database ##
```
sudo /etc/init.d/tomcat6 restart
http://localhost:8080/SRW/search/DSpace
```
You should see a search interface with a number of indexes exposed.  Search away.