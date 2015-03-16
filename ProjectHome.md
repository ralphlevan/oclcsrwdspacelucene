An SRW/SRU interface for DSpace

This project contains the DSpace interface for the OCLC SRW server. This component, when added as a jar to the SRW server, supports SRW/SRU access to DSpace Repositories.
<br />
Installation instructions can be found in InstallationInstructions
<br />
Configuration information can be found in ConfigurationFiles
<br />
Turning on debugging in the DSpace environment turns out to be trickier than I expected.  See [DebuggingInDSpace](DebuggingInDSpace.md) <br />
<br />
The trunk contains the code for the latest version of DSpace.  For DSpace versions up to 1.4.2, use this:
```
svn checkout http://oclcsrwdspacelucene.googlecode.com/svn/tags/DSpace-1.4.2 oclcsrwdspacelucene-1.4.2
```