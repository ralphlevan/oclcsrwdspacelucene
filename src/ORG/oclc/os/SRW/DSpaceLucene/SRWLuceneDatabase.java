/*
   Copyright 2006 OCLC Online Computer Library Center, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */
/*
 * SRWLuceneDatabase.java
 *
 * Created on August 4, 2003, 1:54 PM
 */

package ORG.oclc.os.SRW.DSpaceLucene;

import ORG.oclc.os.SRW.QueryResult;
import ORG.oclc.os.SRW.SRWDatabase;
import ORG.oclc.os.SRW.SRWDiagnostic;
import ORG.oclc.os.SRW.TermList;
import gov.loc.www.zing.srw.TermTypeWhereInList;
import gov.loc.www.zing.srw.ScanRequestType;
import gov.loc.www.zing.srw.SearchRetrieveRequestType;
import gov.loc.www.zing.srw.TermType;
import gov.loc.www.zing.srw.TermsType;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import javax.servlet.http.HttpServletRequest;
import org.apache.axis.MessageContext;
import org.apache.axis.types.NonNegativeInteger;
import org.apache.axis.types.PositiveInteger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dspace.browse.Browse;
import org.dspace.browse.BrowseInfo;
import org.dspace.browse.BrowseScope;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.search.QueryArgs;
import org.dspace.search.QueryResults;
import org.z3950.zing.cql.CQLAndNode;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLNotNode;
import org.z3950.zing.cql.CQLOrNode;
import org.z3950.zing.cql.CQLTermNode;

/**
 *
 * @author  levan
 */
public class SRWLuceneDatabase extends SRWDatabase {
    static Log log=LogFactory.getLog(SRWLuceneDatabase.class);

    Hashtable    indexSynonyms=new Hashtable();
    String       luceneDirectory;

    public void addRenderer(String schemaName, String schemaID, Properties props)
        throws InstantiationException {
    }
    
    protected Object createTransformer(final String schemaName,
      final String fileName) {
        if(fileName.indexOf("default")>=0) {
            transformers.put(schemaName, fileName);
            return fileName;
        }
        return null;
    }

    
    public String getExtraResponseData(QueryResult result, SearchRetrieveRequestType request) {
        return null;
    }
    

    public String getIndexInfo() {
        Enumeration     enumer=dbProperties.propertyNames();
        Hashtable       sets=new Hashtable();
        String          index, indexSet, prop;
        StringBuffer    sb=new StringBuffer("        <indexInfo>\n");
        StringTokenizer st;
        while(enumer.hasMoreElements()) {
            prop=(String)enumer.nextElement();
            if(prop.startsWith("qualifier.")) {
                st=new StringTokenizer(prop.substring(10));
                index=st.nextToken();
                st=new StringTokenizer(index, ".");
                if(st.countTokens()==1) {
                    indexSet="local";
                    index=prop.substring(10);
                }
                else {
                    indexSet=st.nextToken();
                    index=prop.substring(10+indexSet.length()+1);
                }
                if(log.isDebugEnabled())log.debug("indexSet="+indexSet+", index="+index);
                if(sets.get(indexSet)==null) {  // new set
                    sb.append("          <set identifier=\"")
                      .append(dbProperties.getProperty("indexSet."+indexSet))
                      .append("\" name=\"").append(indexSet).append("\"/>\n");
                    sets.put(indexSet, indexSet);
                }
                sb.append("          <index>\n")
                  .append("            <title>").append(indexSet).append('.').append(index).append("</title>\n")
                  .append("            <map>\n")
                  .append("              <name set=\"").append(indexSet).append("\">").append(index).append("</name>\n")
                  .append("              </map>\n")
                  .append("            </index>\n");
            }
        }
        sb.append("          </indexInfo>\n");
        return sb.toString();
    }


    private void getIndexSynonyms(Properties properties) {
        indexSynonyms.put("cql.serverChoice", "");
        if(log.isDebugEnabled())log.debug("new indexSynonym: cql.serverChoice=\"\"");

        if(properties==null) {
            if(log.isDebugEnabled())log.debug("no properties file provided!");
            return;
        }

        Enumeration keys=properties.keys();
        String      index, key, value;

        if(keys==null) {
            if(log.isDebugEnabled())log.debug("no properties specified in properties file");
            return;
        }

        while(keys.hasMoreElements()) {
            key=(String)keys.nextElement();
            if(key.startsWith("indexSynonym.")) {
                value=properties.getProperty(key);
                index=key.substring(13);
                if(log.isDebugEnabled())log.debug("new indexSynonym: "+index+"=\""+value+"\"");
                indexSynonyms.put(index, value);
            }
        }
    }
    

    public QueryResult getQueryResult(String query, SearchRetrieveRequestType request) throws InstantiationException {
        if(log.isDebugEnabled())log.debug("entering SRWLuceneDatabase.doSearchRequest");
        int            collectionID=0, communityID=0;
        MessageContext msgContext=MessageContext.getCurrentContext();
        QueryResults   result;

        try {
            String pathInfo=((HttpServletRequest)msgContext.getProperty(org.apache.axis.transport.http.HTTPConstants.MC_HTTP_SERVLETREQUEST)).getPathInfo();
            if(log.isDebugEnabled())log.debug("pathInfo="+pathInfo);
            int i;
            if((i=pathInfo.indexOf("collection"))>=0)
                collectionID=Integer.parseInt(pathInfo.substring(i+10));
            else if((i=pathInfo.indexOf("community"))>=0)
                communityID=Integer.parseInt(pathInfo.substring(i+9));
            if(collectionID!=0)
                if(log.isDebugEnabled())log.debug("Got a request for collection "+collectionID);
            else
                if(communityID!=0)
                    if(log.isDebugEnabled())log.debug("Got a request for community "+communityID);
                else
                    if(log.isDebugEnabled())log.debug("Got a request for all collections");

            if(log.isDebugEnabled())log.debug("query="+query);
            CQLNode root = parser.parse(query);
            dumpQueryTree(root);
            query=makeLuceneQuery(root);
            if(log.isDebugEnabled())log.debug("lucene query="+query);
            QueryArgs    qArgs=new QueryArgs();
            if(collectionID>0) {
                query="+(" + query + ") +location:\"l" + collectionID + "\"";
                if(log.isDebugEnabled())log.debug("new query="+query);
            }
            else if(communityID>0) {
                query="+(" + query + ") +location:\"m" + communityID + "\"";
                if(log.isDebugEnabled())log.debug("new query="+query);
            }
            qArgs.setQuery( query );

            int startPoint=1;
            PositiveInteger startRec=request.getStartRecord();
            if(startRec!=null)
                startPoint=startRec.intValue();
            qArgs.setStart(startPoint-1); // lucene is zero ordinal?

            int numRecs=defaultNumRecs;
            NonNegativeInteger maxRecords=request.getMaximumRecords();
            if(maxRecords!=null)
                numRecs=maxRecords.intValue();
            qArgs.setPageSize(numRecs);

            return new LuceneQueryResult(qArgs);
        }
        catch(Exception e) {
            LuceneQueryResult lqr=new LuceneQueryResult();
            lqr.addDiagnostic(SRWDiagnostic.GeneralSystemError, e.getMessage());
            return lqr;
        }
    }


    public TermList getTermList(CQLTermNode ctn, int position, int maxTerms,
      ScanRequestType scanRequestType) {
        TermList tl=new TermList();
        if(log.isDebugEnabled())log.debug("entering SRWLuceneDatabase.doScanRequest");
        Context dspaceContext=null;
        int collectionID=0, communityID=0;
        MessageContext msgContext=MessageContext.getCurrentContext();

        try {
            dspaceContext=new Context();
            if(log.isDebugEnabled())log.debug("dspaceContext.isValid()="+dspaceContext.isValid());
            BrowseScope scope = new BrowseScope(dspaceContext);
            String pathInfo=((HttpServletRequest)msgContext.getProperty(org.apache.axis.transport.http.HTTPConstants.MC_HTTP_SERVLETREQUEST)).getPathInfo();
            if(log.isDebugEnabled())log.debug("pathInfo="+pathInfo);
            int i;
            if((i=pathInfo.indexOf("collection"))>=0)
                collectionID=Integer.parseInt(pathInfo.substring(i+10));
            else if((i=pathInfo.indexOf("community"))>=0)
                communityID=Integer.parseInt(pathInfo.substring(i+9));
            if(collectionID!=0) {
                if(log.isDebugEnabled())log.debug("Got a request for collection "+collectionID);
                scope.setScope(Collection.find(dspaceContext, collectionID));
            }
            else
                if(communityID!=0) {
                    if(log.isDebugEnabled())log.debug("Got a request for community "+communityID);
                    scope.setScope(Community.find(dspaceContext, communityID));
                }
                else {
                    if(log.isDebugEnabled())log.debug("Got a request for all collections");
                    scope.setScopeAll();
                }

            scope.setTotal(maxTerms);
            scope.setNumberBefore(position-1);
            String index=ctn.getQualifier(),
                   newIndex=(String)indexSynonyms.get(index);
            if(newIndex!=null)
                index=newIndex;
            scope.setFocus(ctn.getTerm());

            BrowseInfo bi;
            String[] result;
            if(index.equals("author")) {
                bi=Browse.getAuthors(scope);
                result=bi.getStringResults();
            }
            else {
                bi=Browse.getItemsByTitle(scope);
                Item[] item=bi.getItemResults();
                result=new String[item.length];
                for(i=0; i<item.length; i++)
                    result[i]=item[i].getDC("title", Item.ANY, Item.ANY)[0].value;
            }
            if(log.isDebugEnabled())log.debug(bi.getTotal()+" items found");
            List l=bi.getResults();
            if(log.isDebugEnabled())log.debug("results="+l);
            TermsType terms=new TermsType();
            TermType  term[]=new TermType[result.length];
            if(log.isDebugEnabled())log.debug(result.length+" terms found");
            for(i=0; i<result.length; i++) {
                term[i]=new TermType();
                if(i==0 && bi.isFirst())
                    term[i].setWhereInList(TermTypeWhereInList.first);
                if(i==result.length-1 && bi.isLast())
                    if(i==0)
                        term[i].setWhereInList(TermTypeWhereInList.only);
                    else
                        term[i].setWhereInList(TermTypeWhereInList.last);
                term[i].setValue(result[i]);
                if(log.isDebugEnabled())log.debug(result[i]);
                //term[i].setNumberOfRecords(new NonNegativeInteger("0"));
            }
            tl.setTerms(term);
            dspaceContext.complete();
        }
        catch(SQLException e) {
            dspaceContext.abort();
            log.error(e, e);
            tl.addDiagnostic(SRWDiagnostic.GeneralSystemError, e.toString());
        }
        return tl;
    }


    public void init(String dbname, String srwHome, String dbHome,
      String dbPropertiesFileName, Properties dbProperties, HttpServletRequest req) {
        if(log.isDebugEnabled())log.debug("entering SRWLuceneDatabase.init, dbname="+dbname);
        super.initDB(dbname, srwHome, dbHome, dbPropertiesFileName, dbProperties);
        System.setProperty("dspace.configuration", dbHome+"config/dspace.cfg");
        String configProperty = System.getProperty("dspace.configuration");
        if(configProperty!=null) {
            luceneDirectory=ConfigurationManager.getProperty("search.dir");
            if(log.isDebugEnabled())log.debug("lucene directory="+luceneDirectory);
        }
        else
            log.error("no dspace.configuration available");
        getIndexSynonyms(dbProperties);
        
        if(log.isDebugEnabled())log.debug("leaving SRWLuceneDatabase.init");
    }


    private String makeLuceneQuery(CQLNode node) {
        StringBuffer sb=new StringBuffer();
        makeLuceneQuery(node, sb);
        return sb.toString();
    }
    
    
    private void makeLuceneQuery(CQLNode node, StringBuffer sb) {
        if(node instanceof CQLBooleanNode) {
            CQLBooleanNode cbn=(CQLBooleanNode)node;
            sb.append("(");
            makeLuceneQuery(cbn.left, sb);
            if(node instanceof CQLAndNode)
                sb.append(" AND ");
            else if(node instanceof CQLNotNode)
                sb.append(" NOT ");
            else if(node instanceof CQLOrNode)
                sb.append(" OR ");
            else sb.append(" UnknownBoolean("+cbn+") ");
            makeLuceneQuery(cbn.right, sb);
            sb.append(")");
        }
        else if(node instanceof CQLTermNode) {
            CQLTermNode ctn=(CQLTermNode)node;
            String index=ctn.getQualifier(),
                   newIndex=(String)indexSynonyms.get(index);
            if(newIndex!=null)
                index=newIndex;
            if(!index.equals(""))
                sb.append(index).append(":");
            String term=ctn.getTerm();
            if(ctn.getRelation().getBase().equals("=") ||
              ctn.getRelation().getBase().equals("scr")) {
                if(term.indexOf(' ')>=0)
                    sb.append('"').append(term).append('"');
                else
                    sb.append(ctn.getTerm());
            }
            else if(ctn.getRelation().getBase().equals("any")) {
                if(term.indexOf(' ')>=0)
                    sb.append('(').append(term).append(')');
                else
                    sb.append(ctn.getTerm());
            }
            else if(ctn.getRelation().getBase().equals("all")) {
                if(term.indexOf(' ')>=0) {
                    sb.append('(');
                    StringTokenizer st=new StringTokenizer(term);
                    while(st.hasMoreTokens()) {
                        sb.append(st.nextToken());
                        if(st.hasMoreTokens())
                            sb.append(" AND ");
                    }
                    sb.append(')');
                }
                else
                    sb.append(ctn.getTerm());
            }
            else
                sb.append("Unsupported Relation: "+ctn.getRelation().getBase());
        }
        else sb.append("UnknownCQLNode("+node+")");
    }

    
    private void dumpQueryTree(CQLNode node) {
        if(node instanceof CQLBooleanNode) {
            CQLBooleanNode cbn=(CQLBooleanNode)node;
            dumpQueryTree(cbn.left);
            if(node instanceof CQLAndNode)
                if(log.isDebugEnabled())log.debug(" AND ");
            else if(node instanceof CQLNotNode)
                if(log.isDebugEnabled())log.debug(" NOT ");
            else if(node instanceof CQLOrNode)
                if(log.isDebugEnabled())log.debug(" OR ");
            else if(log.isDebugEnabled())log.debug(" UnknownBoolean("+cbn+") ");
            dumpQueryTree(cbn.right);
        }
        else if(node instanceof CQLTermNode) {
            CQLTermNode ctn=(CQLTermNode)node;
            if(log.isDebugEnabled())log.debug("term(qualifier=\""+ctn.getQualifier()+"\" relation=\""+
                ctn.getRelation().getBase()+"\" term=\""+ctn.getTerm()+"\")");
        }
        else if(log.isDebugEnabled())log.debug("UnknownCQLNode("+node+")");
    }
    
    
    public boolean supportsSort() {
        return false;
    }
}