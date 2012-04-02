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

    LuceneQueryResult.java
    Created January 15, 2006, 2:11 PM
 */

package ORG.oclc.os.SRW.DSpaceLucene;

import ORG.oclc.os.SRW.QueryResult;
import ORG.oclc.os.SRW.RecordIterator;
import gov.loc.www.zing.srw.ExtraDataType;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;
import org.dspace.search.DSQuery;
import org.dspace.search.QueryArgs;
import org.dspace.search.QueryResults;

/**
 *
 * @author levan
 */
public class LuceneQueryResult extends QueryResult {
    static Log log=LogFactory.getLog(LuceneQueryResult.class);

    public Item[] resultItems;
    Context dspaceContext=null;
    QueryArgs qArgs;
    QueryResults result=null;

    public LuceneQueryResult() {
    }
    
    public LuceneQueryResult(QueryArgs qArgs) throws InstantiationException {
        this.qArgs=qArgs;
        try {
            dspaceContext=new Context();
            if(log.isDebugEnabled()) {
                Exception e=new Exception("in LuceneQueryResult constructor called by:");
                log.debug(e, e);
                log.debug("dspaceContext.isValid()="+dspaceContext.isValid());
                log.debug("getPageSize="+qArgs.getPageSize()+", getQuery="+qArgs.getQuery()+", getStart="+qArgs.getStart());
//                new Exception().printStackTrace();
            }
            result=DSQuery.doQuery(dspaceContext, qArgs);
            if(log.isDebugEnabled())log.debug(result.getHitHandles().size()+" handles found");

            // now instantiate the results and put them in their buckets
            Integer myType;
            List collectionHandles=new ArrayList(), communityHandles=new ArrayList(),
                 itemHandles=new ArrayList();
            String myHandle;
            for(int i=0; i<result.getHitHandles().size(); i++) {
                myHandle = (String )result.getHitHandles().get(i);
                myType  = (Integer)result.getHitTypes().get(i);

                // add the handle to the appropriate lists
                switch( myType.intValue() ) {
                    case Constants.ITEM:
                        itemHandles.add(myHandle);
                        break;

                    case Constants.COLLECTION:
                        collectionHandles.add(myHandle);
                        break;

                    case Constants.COMMUNITY:
                        communityHandles.add(myHandle);
                        break;
                }
            }

            int numItems=itemHandles.size();
            if(log.isDebugEnabled())log.debug(numItems+" items found");
            resultItems=new Item[numItems];
            for(int i=0; i<numItems; i++) {
                myHandle = (String)itemHandles.get(i);
                if(log.isDebugEnabled())log.debug("about to resolveToObject");
                Object o = HandleManager.resolveToObject(dspaceContext, myHandle);
                if(log.isDebugEnabled())log.debug("did resolveToObject");

                resultItems[i] = (Item)o;
                if (resultItems[i] == null)
                    throw new RemoteException("Query \"" + qArgs.getQuery() +
                        "\" returned unresolvable handle: " + myHandle);
            }

            int postings=result.getHitCount();
    //            int postings=itemHandles.size();
            if(log.isDebugEnabled())log.debug("'" + qArgs.getQuery() + "'==> " + postings);
        }
        catch(Exception e) {
            if(dspaceContext!=null)
                dspaceContext.abort();
            throw new InstantiationException(e.getMessage());
        }
    }

    public void close() {
        try {
            if(log.isDebugEnabled())log.debug("freeing DSpace Context: " + dspaceContext);
            if(dspaceContext!=null)
                dspaceContext.complete();
        } catch (SQLException ex) {
            log.error(ex, ex);
        }
        super.close();
    }


    public long getNumberOfRecords() {
        if(result==null) // probably just holding diagnostics
            return 0;
        return result.getHitCount();
    }

    public RecordIterator newRecordIterator(long startPoint, int numRecs, String schemaID, ExtraDataType edt)
      throws InstantiationException {
        if(result==null)
            throw new InstantiationException("No results created");
        if(startPoint-1==qArgs.getStart())
            return new LuceneRecordIterator(this, startPoint, numRecs, edt, false);
        qArgs.setStart((int)(startPoint - 1));
        qArgs.setPageSize(numRecs);
        return new LuceneRecordIterator(new LuceneQueryResult(qArgs), startPoint, numRecs, edt, true);
    }
}
