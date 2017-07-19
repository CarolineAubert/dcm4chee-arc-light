/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2017
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.audit;

import org.dcm4che3.audit.*;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.*;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditLoggerDeviceExtension;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ShowPatientInfo;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.entity.RejectionState;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.stgcmt.StgCmtEventInfo;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2016
 */
@ApplicationScoped
public class AuditService {
    private final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private final String studyDate = "StudyDate";
    private final String keycloakClassName = "org.keycloak.KeycloakSecurityContext";

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;


    boolean hasAuditLoggers() {
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        return ext != null && !ext.getAuditLoggers().isEmpty();
    }

    private void aggregateAuditMessage(AuditLogger auditLogger, Path path) throws IOException {
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.fromFile(path);
        if (path.toFile().length() == 0)
            throw new IOException("Attempt to read from an empty file. ");
        SpoolFileReader readerObj = eventType.eventClass != AuditServiceUtils.EventClass.QUERY
                ? new SpoolFileReader(path) : null;
        Calendar eventTime = getEventTime(path, auditLogger);
        switch (eventType.eventClass) {
            case APPLN_ACTIVITY:
                auditApplicationActivity(auditLogger, readerObj, eventTime, eventType);
                break;
            case CONN_REJECT:
                auditConnectionRejected(auditLogger, readerObj, eventTime, eventType);
                break;
            case STORE_WADOR:
                auditStoreOrWADORetrieve(auditLogger, readerObj, eventTime, eventType);
                break;
            case RETRIEVE:
                auditRetrieve(auditLogger, readerObj, eventTime, eventType);
                break;
            case DELETE:
            case PERM_DELETE:
                auditDeletion(auditLogger, readerObj, eventTime, eventType);
                break;
            case QUERY:
                auditQuery(auditLogger, path, eventTime, eventType);
                break;
            case HL7:
                auditPatientRecord(auditLogger, readerObj, eventTime, eventType);
                break;
            case PROC_STUDY:
                auditProcedureRecord(auditLogger, readerObj, eventTime, eventType);
                break;
            case PROV_REGISTER:
                auditProvideAndRegister(auditLogger, readerObj, eventTime, eventType);
                break;
            case STGCMT:
                auditStorageCommit(auditLogger, readerObj, eventTime, eventType);
                break;
        }
    }

    void spoolApplicationActivity(AuditServiceUtils.EventType eventType, HttpServletRequest req) {
        if (eventType == null)
            return;
        LinkedHashSet<Object> objs = new LinkedHashSet<>();
        objs.add(new AuditInfo(new BuildAuditInfo.Builder().calledAET(getAET(device)).build()));
        if (req != null) {
            String callingUser = req.getAttribute(keycloakClassName) != null ? getPreferredUsername(req) : req.getRemoteAddr();
            objs.add(new AuditInfo(
                    new BuildAuditInfo.Builder().callingAET(callingUser).callingHost(req.getRemoteAddr()).build()));
        }
        writeSpoolFile(String.valueOf(eventType), objs);
    }

    private void auditApplicationActivity(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType eventType) {
        BuildActiveParticipant ap2 = null;
        AuditInfo archiveInfo = new AuditInfo(readerObj.getMainInfo());
        if (!readerObj.getInstanceLines().isEmpty()) {
            AuditInfo callerInfo = new AuditInfo(readerObj.getInstanceLines().iterator().next());
            ap2 = new BuildActiveParticipant.Builder(
                    callerInfo.getField(AuditInfo.CALLING_AET), callerInfo.getField(AuditInfo.CALLING_HOST)).
                    requester(eventType.isSource).roleIDCode(eventType.source).build();
        }
        EventIdentification ei = getEI(eventType, null, eventTime);
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(archiveInfo.getField(AuditInfo.CALLED_AET),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID()).requester(eventType.isDest)
                .roleIDCode(eventType.destination).build();
        emitAuditMessage(ei, !readerObj.getInstanceLines().isEmpty() ? getApList(ap1, ap2) : getApList(ap1), null, auditLogger);
    }

    void spoolInstancesDeleted(StoreContext ctx) {
        Attributes attrs = ctx.getAttributes();
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (Attributes studyRef : attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence))
            for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence))
                for (Attributes sopRef : seriesRef.getSequence(Tag.ReferencedSOPSequence))
                    buildSOPClassMap(sopClassMap, sopRef.getString(Tag.ReferencedSOPClassUID),
                            sopRef.getString(Tag.ReferencedSOPInstanceUID));
        LinkedHashSet<Object> deleteObjs = getDeletionObjsForSpooling(sopClassMap, new AuditInfo(getAIStoreCtx(ctx)));
        String eventType = ctx.getStoredInstance().getSeries().getStudy().getRejectionState()== RejectionState.COMPLETE
        ? String.valueOf(AuditServiceUtils.EventType.RJ_COMPLET) : String.valueOf(AuditServiceUtils.EventType.RJ_PARTIAL);
        writeSpoolFile(eventType, deleteObjs);
    }

    void spoolStudyDeleted(StudyDeleteContext ctx) {
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (org.dcm4chee.arc.entity.Instance i : ctx.getInstances())
            buildSOPClassMap(sopClassMap, i.getSopClassUID(), i.getSopInstanceUID());
        Study s = ctx.getStudy();
        Patient p = ctx.getPatient();
        HttpServletRequest request = ctx.getHttpRequest();
        BuildAuditInfo i = request != null ? buildPermDeletionAuditInfoForWeb(request, ctx, s, p)
                : buildPermDeletionAuditInfoForScheduler(ctx, s, p);
        String eventType = String.valueOf(request != null ? AuditServiceUtils.EventType.PRMDLT_WEB : AuditServiceUtils.EventType.PRMDLT_SCH);
        LinkedHashSet<Object> deleteObjs = getDeletionObjsForSpooling(sopClassMap, new AuditInfo(i));
        writeSpoolFile(eventType, deleteObjs);
    }

    private BuildAuditInfo buildPermDeletionAuditInfoForWeb(HttpServletRequest req, StudyDeleteContext ctx, Study s, Patient p) {
        String callingAET = req.getAttribute(keycloakClassName) != null ? getPreferredUsername(req) : req.getRemoteAddr();
        return new BuildAuditInfo.Builder().callingAET(callingAET).callingHost(req.getRemoteHost()).calledAET(req.getRequestURI())
                .studyUID(s.getStudyInstanceUID()).accNum(s.getAccessionNumber())
                .pID(getPID(p.getAttributes())).outcome(getOD(ctx.getException())).studyDate(s.getStudyDate())
                .pName(pName(p.getAttributes())).build();
    }

    private BuildAuditInfo buildPermDeletionAuditInfoForScheduler(StudyDeleteContext ctx, Study s, Patient p) {
        return new BuildAuditInfo.Builder().studyUID(s.getStudyInstanceUID()).accNum(s.getAccessionNumber())
                .pID(getPID(p.getAttributes())).outcome(getOD(ctx.getException())).studyDate(s.getStudyDate())
                .pName(pName(p.getAttributes())).build();
    }

    private void auditDeletion(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType eventType) {
        AuditInfo dI = new AuditInfo(readerObj.getMainInfo());
        EventIdentification ei = getCustomEI(eventType, dI.getField(AuditInfo.OUTCOME),
                dI.getField(AuditInfo.WARNING), eventTime);
        BuildActiveParticipant ap1 = null;
        if (eventType.isSource) {
            ap1 = new BuildActiveParticipant.Builder(
                    dI.getField(AuditInfo.CALLING_AET), dI.getField(AuditInfo.CALLING_HOST))
                    .requester(eventType.isSource).build();
        }
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(
                eventType.isSource ? dI.getField(AuditInfo.CALLED_AET) : getAET(device),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                .requester(eventType.isDest).build();
        ParticipantObjectContainsStudy pocs = getPocs(dI.getField(AuditInfo.STUDY_UID));
        BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(
                getSopClasses(readerObj.getInstanceLines()), pocs)
                .acc(eventType.eventClass == AuditServiceUtils.EventClass.PERM_DELETE
                        ? getAccessions(dI.getField(AuditInfo.ACC_NUM)) : null).build();
        BuildParticipantObjectIdentification poi1 = new BuildParticipantObjectIdentification.Builder(
                dI.getField(AuditInfo.STUDY_UID), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(desc)).detail(getPod(studyDate, dI.getField(AuditInfo.STUDY_DATE))).build();
        BuildParticipantObjectIdentification poi2 = new BuildParticipantObjectIdentification.Builder(
                dI.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(dI.getField(AuditInfo.P_NAME)).build();
        emitAuditMessage(ei, eventType.isSource ? getApList(ap1, ap2) : getApList(ap2),
                getPoiList(poi1, poi2), auditLogger);
    }

    void spoolConnectionRejected(Connection conn, Socket s, Throwable e) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(s.getRemoteSocketAddress().toString())
                .calledHost(conn.getHostname()).outcome(e.getMessage()).build();
        obj.add(new AuditInfo(i));
        String eventType = String.valueOf(AuditServiceUtils.EventType.CONN__RJCT);
        writeSpoolFile(eventType, obj);
    }

    private void auditConnectionRejected(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType eventType) {
        AuditInfo crI = new AuditInfo(readerObj.getMainInfo());
        EventIdentification ei = getEI(eventType, crI.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(getAET(device),
                crI.getField(AuditInfo.CALLED_HOST)).altUserID(AuditLogger.processID()).requester(false).build();
        String userID, napID;
        userID = napID = crI.getField(AuditInfo.CALLING_HOST);
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(userID, napID).requester(true).build();
        BuildParticipantObjectIdentification poi = new BuildParticipantObjectIdentification.Builder(
                crI.getField(AuditInfo.CALLING_HOST), AuditMessages.ParticipantObjectIDTypeCode.NodeID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, null).build();
        emitAuditMessage(ei, getApList(ap1, ap2), getPoiList(poi), auditLogger);
    }

    void spoolQuery(QueryContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forQuery(ctx);
        AuditInfo auditInfo = ctx.getHttpRequest() != null ? createAuditInfoForQIDO(ctx) : createAuditInfoForFIND(ctx);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path directory = Paths.get(StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()),
                                auditLogger.getCommonName().replaceAll(" ", "_"));
                try {
                    Files.createDirectories(directory);
                    Path file = Files.createTempFile(directory, String.valueOf(eventType), null);
                    try (BufferedOutputStream out = new BufferedOutputStream(
                            Files.newOutputStream(file, StandardOpenOption.APPEND))) {
                        new DataOutputStream(out).writeUTF(auditInfo.toString());
                        if (ctx.getAssociation() != null) {
                            try (DicomOutputStream dos = new DicomOutputStream(out, UID.ImplicitVRLittleEndian)) {
                                dos.writeDataset(null, ctx.getQueryKeys());
                            } catch (Exception e) {
                                LOG.warn("Failed to create DicomOutputStream : ", e);
                            }
                        }
                    }
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File - {}", auditLogger.getCommonName(), e);
                }
            }
        }
    }

    void auditAndProcessFile(AuditLogger auditLogger, Path file) {
        try {
            aggregateAuditMessage(auditLogger, file);
            Files.delete(file);
        } catch (Exception e) {
            LOG.warn("Failed to process Audit Spool File - {}", auditLogger.getCommonName(), file, e);
            try {
                Files.move(file, file.resolveSibling(file.getFileName().toString() + ".failed"));
            } catch (IOException e1) {
                LOG.warn("Failed to mark Audit Spool File - {} as failed", auditLogger.getCommonName(), file, e);
            }
        }
    }

    private AuditInfo createAuditInfoForFIND(QueryContext ctx) {
        return new AuditInfo(
                new BuildAuditInfo.Builder()
                        .callingHost(ctx.getRemoteHostName())
                        .callingAET(ctx.getCallingAET())
                        .calledAET(ctx.getCalledAET())
                        .queryPOID(ctx.getSOPClassUID())
                        .build());
    }

    private AuditInfo createAuditInfoForQIDO(QueryContext ctx) {
        HttpServletRequest httpRequest = ctx.getHttpRequest();
        return new AuditInfo(
                new BuildAuditInfo.Builder()
                        .callingHost(ctx.getRemoteHostName())
                        .callingAET(httpRequest.getAttribute(keycloakClassName) != null
                                ? getPreferredUsername(httpRequest)
                                : ctx.getRemoteHostName())
                        .calledAET(httpRequest.getRequestURI())
                        .queryPOID(ctx.getSearchMethod())
                        .queryString(httpRequest.getRequestURI() + httpRequest.getQueryString())
                        .build());
    }

    private void auditQuery(
            AuditLogger auditLogger, Path file, Calendar eventTime, AuditServiceUtils.EventType eventType) throws IOException {
        AuditInfo qrI;
        List<ActiveParticipant> apList;
        List<ParticipantObjectIdentification> poiList;
        EventIdentification ei = getEI(eventType, null, eventTime);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            qrI = new AuditInfo(new DataInputStream(in).readUTF());
            BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(qrI.getField(AuditInfo.CALLING_AET),
                    qrI.getField(AuditInfo.CALLING_HOST)).requester(eventType.isSource).roleIDCode(eventType.source)
                    .build();
            BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(qrI.getField(AuditInfo.CALLED_AET),
                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                    .requester(eventType.isDest).roleIDCode(eventType.destination).build();
            apList = getApList(ap1, ap2);
            BuildParticipantObjectIdentification poi;
            if (eventType == AuditServiceUtils.EventType.QUERY_QIDO) {
                poi = new BuildParticipantObjectIdentification.Builder(
                        qrI.getField(AuditInfo.Q_POID), AuditMessages.ParticipantObjectIDTypeCode.QIDO_QUERY,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Query)
                        .query(qrI.getField(AuditInfo.Q_STRING).getBytes())
                        .detail(getPod("QueryEncoding", String.valueOf(StandardCharsets.UTF_8))).build();
            }
            else {
                byte[] buffer = new byte[(int) Files.size(file)];
                int len = in.read(buffer);
                byte[] data;
                if (len != -1) {
                    data = new byte[len];
                    System.arraycopy(buffer, 0, data, 0, len);
                }
                else {
                    data = new byte[0];
                }
                poi = new BuildParticipantObjectIdentification.Builder(
                        qrI.getField(AuditInfo.Q_POID), AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Report).query(data)
                        .detail(getPod("TransferSyntax", UID.ImplicitVRLittleEndian)).build();
            }
            poiList = getPoiList(poi);
        }
        emitAuditMessage(ei, apList, poiList, auditLogger);
    }

    void spoolInstanceStoredOrWadoRetrieve(StoreContext sCtx, RetrieveContext rCtx) {
        if (sCtx != null) {
            AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forInstanceStored(sCtx);
            if (eventType == null)
                return; // no audit message for duplicate received instance
            String callingAET = sCtx.getStoreSession().getHttpRequest() != null
                    ? sCtx.getStoreSession().getHttpRequest().getRemoteAddr() : sCtx.getStoreSession().getCallingAET().replace('|', '-');
            String fileName = getFileName(eventType, callingAET, sCtx.getStoreSession().getCalledAET(), sCtx.getStudyInstanceUID());
            BuildAuditInfo i = getAIStoreCtx(sCtx);
            BuildAuditInfo iI = new BuildAuditInfo.Builder().sopCUID(sCtx.getSopClassUID()).sopIUID(sCtx.getSopInstanceUID())
                    .mppsUID(StringUtils.maskNull(sCtx.getMppsInstanceUID(), " ")).build();
            writeSpoolFileStoreOrWadoRetrieve(fileName, new AuditInfo(i), new AuditInfo(iI));
        }
        if (rCtx != null) {
            HttpServletRequest req = rCtx.getHttpRequest();
            Collection<InstanceLocations> il = rCtx.getMatches();
            Attributes attrs = new Attributes();
            for (InstanceLocations i : il)
                attrs = i.getAttributes();
            String fileName = getFileName(AuditServiceUtils.EventType.WADO___URI, req.getRemoteAddr(),
                    rCtx.getLocalAETitle(), rCtx.getStudyInstanceUIDs()[0]);
            String callingAET = req.getAttribute(keycloakClassName) != null
                    ? getPreferredUsername(req) : req.getRemoteAddr();
            AuditInfo i = new AuditInfo(new BuildAuditInfo.Builder().callingHost(req.getRemoteAddr()).callingAET(callingAET)
                    .calledAET(req.getRequestURI()).studyUID(rCtx.getStudyInstanceUIDs()[0])
                    .accNum(getAcc(attrs)).pID(getPID(attrs)).pName(pName(attrs)).studyDate(getSD(attrs))
                    .outcome(null != rCtx.getException() ? rCtx.getException().getMessage() : null).build());
            AuditInfo iI = new AuditInfo(
                    new BuildAuditInfo.Builder().sopCUID(sopCUID(attrs)).sopIUID(rCtx.getSopInstanceUIDs()[0]).build());
            writeSpoolFileStoreOrWadoRetrieve(fileName, i, iI);
        }
    }

    private void buildSOPClassMap(HashMap<String, HashSet<String>> sopClassMap, String cuid, String iuid) {
        HashSet<String> iuids = sopClassMap.get(cuid);
        if (iuids == null) {
            iuids = new HashSet<>();
            sopClassMap.put(cuid, iuids);
        }
        iuids.add(iuid);
    }

    private void auditStoreOrWADORetrieve(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime,
                                          AuditServiceUtils.EventType eventType) {
        HashSet<String> mppsUIDs = new HashSet<>();
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        AuditInfo i = new AuditInfo(readerObj.getMainInfo());
        for (String line : readerObj.getInstanceLines()) {
            AuditInfo iI = new AuditInfo(line);
            buildSOPClassMap(sopClassMap, iI.getField(AuditInfo.SOP_CUID), iI.getField(AuditInfo.SOP_IUID));
            mppsUIDs.add(iI.getField(AuditInfo.MPPS_UID));
        }
        mppsUIDs.remove(" ");
        EventIdentification ei = getEI(eventType, i.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(
                i.getField(AuditInfo.CALLING_AET),
                i.getField(AuditInfo.CALLING_HOST)).requester(eventType.isSource)
                .roleIDCode(eventType.source).build();
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(
                i.getField(AuditInfo.CALLED_AET), getLocalHostName(auditLogger))
                .altUserID(AuditLogger.processID()).requester(eventType.isDest).roleIDCode(eventType.destination).build();
        HashSet<SOPClass> sopC = new HashSet<>();
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet())
            sopC.add(getSOPC(null, entry.getKey(), entry.getValue().size()));
        ParticipantObjectContainsStudy pocs = getPocs(i.getField(AuditInfo.STUDY_UID));
        BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(sopC, pocs)
                .acc(getAccessions(i.getField(AuditInfo.ACC_NUM)))
                .mpps(AuditMessages.getMPPS(mppsUIDs.toArray(new String[mppsUIDs.size()]))).build();
        String lifecycle = (eventType == AuditServiceUtils.EventType.STORE_CREA
                || eventType == AuditServiceUtils.EventType.STORE_UPDT)
                ? AuditMessages.ParticipantObjectDataLifeCycle.OriginationCreation : null;
        BuildParticipantObjectIdentification poi1 = new BuildParticipantObjectIdentification.Builder(
                i.getField(AuditInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(desc)).detail(getPod(studyDate, i.getField(AuditInfo.STUDY_DATE))).lifeCycle(lifecycle).build();
        BuildParticipantObjectIdentification poi2 = new BuildParticipantObjectIdentification.Builder(
                i.getField(AuditInfo.P_ID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(i.getField(AuditInfo.P_NAME)).build();
        emitAuditMessage(ei, getApList(ap1, ap2), getPoiList(poi1, poi2), auditLogger);
    }

    void spoolPartialRetrieve(RetrieveContext ctx, HashSet<AuditServiceUtils.EventType> et) {
        List<String> failedList = Arrays.asList(ctx.failedSOPInstanceUIDs());
        Collection<InstanceLocations> instanceLocations = ctx.getMatches();
        HashSet<InstanceLocations> failed = new HashSet<>();
        HashSet<InstanceLocations> success = new HashSet<>();
        success.addAll(instanceLocations);
        for (InstanceLocations il : instanceLocations) {
            if (failedList.contains(il.getSopInstanceUID())) {
                failed.add(il);
                success.remove(il);
            }
        }
        String etFile;
        for (AuditServiceUtils.EventType eventType : et) {
            etFile = String.valueOf(eventType);
            if (etFile.substring(9, 10).equals("E"))
                spoolRetrieve(etFile, ctx, failed);
            if (etFile.substring(9, 10).equals("P"))
                spoolRetrieve(etFile, ctx, success);
        }
    }

    void spoolRetrieve(String etFile, RetrieveContext ctx, Collection<InstanceLocations> il) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        HttpServletRequest req = ctx.getHttpRequest();
        String destAET = req != null ? req.getAttribute(keycloakClassName) != null
                ? getPreferredUsername(req) : req.getRemoteAddr() : ctx.getDestinationAETitle();
        String outcome = (etFile.substring(0, 8).equals("RTRV_BGN") && ctx.getException() != null) || etFile.substring(9, 10).equals("E")
                ? getFailOutcomeDesc(ctx) : null;
        String warning = etFile.substring(9, 10).equals("P") && ctx.warning() != 0
                ? ctx.warning() == ctx.getMatches().size() ? "Warnings on retrieve of all instances"
                : "Warnings on retrieve of " + ctx.warning() + " instances" : null;
        boolean failedIUIDShow = etFile.substring(9, 10).equals("E") && ctx.failedSOPInstanceUIDs().length > 0;
        BuildAuditInfo i = new BuildAuditInfo.Builder().calledAET(req != null ? req.getRequestURI() : ctx.getLocalAETitle())
                .destAET(destAET).destNapID(null != req ? req.getRemoteAddr() : ctx.getDestinationHostName()).warning(warning)
                .callingHost(ctx.getRequestorHostName()).moveAET(ctx.getMoveOriginatorAETitle()).outcome(outcome).failedIUIDShow(failedIUIDShow).build();
        obj.add(new AuditInfo(i));
        for (InstanceLocations instanceLocation : il)
            addInstanceInfoForRetrieve(obj, instanceLocation);
        for (InstanceLocations instanceLocationCStoreForward : ctx.getCStoreForwards())
            addInstanceInfoForRetrieve(obj, instanceLocationCStoreForward);
        writeSpoolFile(etFile, obj);
    }

    private void addInstanceInfoForRetrieve(LinkedHashSet<Object> obj, InstanceLocations instanceLocation) {
        Attributes attrs = instanceLocation.getAttributes();
        BuildAuditInfo iI = new BuildAuditInfo.Builder().studyUID(attrs.getString(Tag.StudyInstanceUID)).accNum(getAcc(attrs))
                .sopCUID(sopCUID(attrs)).sopIUID(attrs.getString(Tag.SOPInstanceUID)).pID(getPID(attrs))
                .pName(pName(attrs)).studyDate(getSD(attrs)).build();
        obj.add(new AuditInfo(iI));
    }


    private void auditRetrieve(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType eventType) {
        AuditInfo ri = new AuditInfo(readerObj.getMainInfo());
        EventIdentification ei = getCustomEI(eventType, ri.getField(AuditInfo.OUTCOME),
                ri.getField(AuditInfo.WARNING), eventTime);
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(ri.getField(AuditInfo.CALLED_AET),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID()).requester(eventType.isSource)
                .roleIDCode(eventType.source).build();
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(ri.getField(AuditInfo.DEST_AET),
                ri.getField(AuditInfo.DEST_NAP_ID)).requester(eventType.isDest).roleIDCode(eventType.destination).build();
        BuildActiveParticipant ap3 = null;
        if (eventType.isOther) {
            ap3 = new BuildActiveParticipant.Builder(ri.getField(AuditInfo.MOVEAET),
                    ri.getField(AuditInfo.CALLING_HOST)).requester(eventType.isOther).build();
        }
        HashMap<String, AccessionNumSopClassInfo> study_accNumSOPClassInfo = new HashMap<>();
        String pID = device.getDeviceExtension(ArchiveDeviceExtension.class).auditUnknownPatientID();
        String pName = null;
        String studyDt = null;
        for (String line : readerObj.getInstanceLines()) {
            AuditInfo rInfo = new AuditInfo(line);
            String studyInstanceUID = rInfo.getField(AuditInfo.STUDY_UID);
            AccessionNumSopClassInfo accNumSopClassInfo = study_accNumSOPClassInfo.get(studyInstanceUID);
            if (accNumSopClassInfo == null) {
                accNumSopClassInfo = new AccessionNumSopClassInfo(
                        rInfo.getField(AuditInfo.ACC_NUM));
                study_accNumSOPClassInfo.put(studyInstanceUID, accNumSopClassInfo);
            }
            accNumSopClassInfo.addSOPInstance(rInfo);
            study_accNumSOPClassInfo.put(studyInstanceUID, accNumSopClassInfo);
            pID = rInfo.getField(AuditInfo.P_ID);
            pName = rInfo.getField(AuditInfo.P_NAME);
            studyDt = rInfo.getField(AuditInfo.STUDY_DATE);
        }
        List<BuildParticipantObjectIdentification> pois = new ArrayList<>();
        for (Map.Entry<String, AccessionNumSopClassInfo> entry : study_accNumSOPClassInfo.entrySet()) {
            HashSet<SOPClass> sopC = new HashSet<>();
            for (Map.Entry<String, HashSet<String>> sopClassMap : entry.getValue().getSopClassMap().entrySet()) {
                if (ri.getField(AuditInfo.FAILED_IUID_SHOW) != null)
                    sopC.add(getSOPC(sopClassMap.getValue(), sopClassMap.getKey(), sopClassMap.getValue().size()));
                else
                    sopC.add(getSOPC(null, sopClassMap.getKey(), sopClassMap.getValue().size()));
            }
            BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(sopC, getPocs(entry.getKey()))
                    .acc(getAccessions(entry.getValue().getAccNum())).build();
            BuildParticipantObjectIdentification poi = new BuildParticipantObjectIdentification.Builder(
                    entry.getKey(), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                    AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                    .desc(getPODesc(desc)).detail(getPod(studyDate, studyDt)).build();
            pois.add(poi);
        }
        BuildParticipantObjectIdentification poiPatient = new BuildParticipantObjectIdentification.Builder(
                pID, AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(pName).build();
        pois.add(poiPatient);
        emitAuditMessage(ei, eventType.isOther ? getApList(ap1, ap2, ap3) : getApList(ap1, ap2),
                getPoiList(pois.toArray(new BuildParticipantObjectIdentification[pois.size()])), auditLogger);
    }

    void spoolPatientRecord(PatientMgtContext ctx) {
        HashSet<AuditServiceUtils.EventType> et = AuditServiceUtils.EventType.forHL7(ctx);
        for (AuditServiceUtils.EventType eventType : et) {
            LinkedHashSet<Object> obj = new LinkedHashSet<>();
            String source = null;
            String dest = null;
            String hl7MessageType = null;
            HL7Segment msh = ctx.getHL7MessageHeader();
            if (ctx.getHttpRequest() != null) {
                source = ctx.getHttpRequest().getAttribute(keycloakClassName) != null
                        ? getPreferredUsername(ctx.getHttpRequest())
                        : ctx.getHttpRequest().getRemoteAddr();
                dest = ctx.getCalledAET();
            }
            if (msh != null) {
                source = msh.getSendingApplicationWithFacility();
                dest = msh.getReceivingApplicationWithFacility();
                hl7MessageType = msh.getMessageType();
            }
            if (ctx.getAssociation() != null) {
                source = ctx.getAssociation().getCallingAET();
                dest = ctx.getAssociation().getCalledAET();
            }
            String pID = eventType == AuditServiceUtils.EventType.PAT_DELETE && ctx.getPreviousPatientID() != null
                    ? getPlainOrHashedPatientID(ctx.getPreviousPatientID().toString())
                    : ctx.getPatientID() != null ? getPlainOrHashedPatientID(ctx.getPatientID().toString())
                    : device.getDeviceExtension(ArchiveDeviceExtension.class).auditUnknownPatientID();
            String pName = eventType == AuditServiceUtils.EventType.PAT_DELETE && ctx.getPreviousAttributes() != null
                    ? StringUtils.maskEmpty(pName(ctx.getPreviousAttributes()), null)
                    : StringUtils.maskEmpty(pName(ctx.getAttributes()), null);
            String callingHost = ctx.getHttpRequest() != null
                    ? ctx.getHttpRequest().getRemoteAddr()
                    : msh != null || ctx.getAssociation() != null
                    ? ctx.getRemoteHostName() : null;
            BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(callingHost)
                    .callingAET(source).calledAET(dest).pID(pID).pName(pName)
                    .outcome(getOD(ctx.getException())).hl7MessageType(hl7MessageType).build();
            obj.add(new AuditInfo(i));
            writeSpoolFile(String.valueOf(eventType), obj);
        }
    }

    private void auditPatientRecord(
            AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType et) {
        AuditInfo hl7I = new AuditInfo(readerObj.getMainInfo());
        EventIdentification ei = getEI(et, hl7I.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant ap1 = null;
        if (et.isSource)
             ap1 = new BuildActiveParticipant.Builder(hl7I.getField(AuditInfo.CALLING_AET),
                hl7I.getField(AuditInfo.CALLING_HOST)).requester(et.isSource).roleIDCode(et.source).build();
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(
                et.isSource ? hl7I.getField(AuditInfo.CALLED_AET) : getAET(device),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID()).requester(et.isDest)
                .roleIDCode(et.destination).build();
        BuildParticipantObjectIdentification poi = new BuildParticipantObjectIdentification.Builder(
                hl7I.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(hl7I.getField(AuditInfo.P_NAME)).detail(getPod("HL7MessageType", hl7I.getField(AuditInfo.HL7_MESSAGE_TYPE))).build();
        emitAuditMessage(ei, et.isSource ? getApList(ap1, ap2) : getApList(ap2), getPoiList(poi), auditLogger);
    }

    void spoolProcedureRecord(ProcedureContext ctx) {
        HashSet<AuditServiceUtils.EventType> et = AuditServiceUtils.EventType.forProcedure(ctx.getEventActionCode());
        for (AuditServiceUtils.EventType eventType : et) {
            LinkedHashSet<Object> obj = new LinkedHashSet<>();
            BuildAuditInfo i = ctx.getHttpRequest() != null
                    ? buildAuditInfoFORRestful(ctx)
                    : ctx.getAssociation() != null ? buildAuditInfoForAssociation(ctx) : buildAuditInfoFORHL7(ctx);
            obj.add(new AuditInfo(i));
            writeSpoolFile(String.valueOf(eventType), obj);
        }
    }

    private BuildAuditInfo buildAuditInfoForAssociation(ProcedureContext ctx) {
        Association as = ctx.getAssociation();
        Attributes attr = ctx.getAttributes();
        Patient p = ctx.getPatient();
        Attributes pAttr = p.getAttributes();
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ctx.getRemoteHostName()).callingAET(as.getCallingAET())
                .calledAET(as.getCalledAET()).studyUID(ctx.getStudyInstanceUID()).accNum(getAcc(attr))
                .pID(getPID(pAttr)).pName(pName(pAttr)).outcome(getOD(ctx.getException())).studyDate(getSD(attr)).build();
        return i;
    }

    private BuildAuditInfo buildAuditInfoFORRestful(ProcedureContext ctx) {
        Attributes attr = ctx.getAttributes();
        HttpServletRequest req  = ctx.getHttpRequest();
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ctx.getRemoteHostName())
                .callingAET(req.getAttribute(keycloakClassName) != null ? getPreferredUsername(req) : req.getRemoteAddr())
                .calledAET(ctx.getCalledAET()).studyUID(ctx.getStudyInstanceUID())
                .accNum(getAcc(attr)).pID(getPID(attr)).pName(pName(ctx.getPatient().getAttributes()))
                .outcome(getOD(ctx.getException())).studyDate(getSD(attr)).build();
        return i;
    }

    private BuildAuditInfo buildAuditInfoFORHL7(ProcedureContext ctx) {
        Attributes attr = ctx.getAttributes();
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ctx.getRemoteHostName())
                .callingAET(ctx.getHL7MessageHeader().getSendingApplicationWithFacility())
                .calledAET(ctx.getHL7MessageHeader().getReceivingApplicationWithFacility()).studyUID(ctx.getStudyInstanceUID())
                .accNum(getAcc(attr)).pID(getPID(attr)).pName(pName(ctx.getPatient().getAttributes()))
                .outcome(getOD(ctx.getException())).studyDate(getSD(attr)).build();
        return i;
    }

    void spoolProcedureRecord(StudyMgtContext ctx) {
        HashSet<AuditServiceUtils.EventType> et = AuditServiceUtils.EventType.forProcedure(ctx.getEventActionCode());
        for (AuditServiceUtils.EventType eventType : et) {
            LinkedHashSet<Object> obj = new LinkedHashSet<>();
            String callingAET = ctx.getHttpRequest().getAttribute(keycloakClassName) != null
                    ? getPreferredUsername(ctx.getHttpRequest()) : ctx.getHttpRequest().getRemoteAddr();
            Attributes sAttr = ctx.getAttributes();
            Attributes pAttr = ctx.getStudy() != null ? ctx.getStudy().getPatient().getAttributes() : null;
            BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ctx.getHttpRequest().getRemoteHost()).callingAET(callingAET)
                    .calledAET(ctx.getApplicationEntity().getAETitle()).studyUID(ctx.getStudyInstanceUID()).accNum(getAcc(sAttr))
                    .pID(getPID(pAttr)).pName(pName(pAttr)).outcome(getOD(ctx.getException())).studyDate(getSD(sAttr)).build();
            obj.add(new AuditInfo(i));
            writeSpoolFile(String.valueOf(eventType), obj);
        }
    }

    private void auditProcedureRecord(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType et) {
        AuditInfo prI = new AuditInfo(readerObj.getMainInfo());
        EventIdentification ei = getEI(et, prI.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(prI.getField(AuditInfo.CALLING_AET),
                prI.getField(AuditInfo.CALLING_HOST)).requester(et.isSource).build();
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(prI.getField(AuditInfo.CALLED_AET),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID()).requester(et.isDest).build();
        ParticipantObjectContainsStudy pocs = getPocs(prI.getField(AuditInfo.STUDY_UID));
        BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(null, pocs)
                .acc(getAccessions(prI.getField(AuditInfo.ACC_NUM))).build();
        BuildParticipantObjectIdentification poi1 = new BuildParticipantObjectIdentification.Builder(
                prI.getField(AuditInfo.STUDY_UID), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(desc)).detail(getPod(studyDate, prI.getField(AuditInfo.STUDY_DATE))).build();
        BuildParticipantObjectIdentification poi2 = new BuildParticipantObjectIdentification.Builder(
                prI.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(prI.getField(AuditInfo.P_NAME)).build();
        emitAuditMessage(ei, getApList(ap1, ap2), getPoiList(poi1, poi2), auditLogger);
    }

    void spoolProvideAndRegister(ExportContext ctx) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        Attributes xdsiManifest = ctx.getXDSiManifest();
        if (xdsiManifest == null)
            return;
        URI dest = ctx.getExporter().getExporterDescriptor().getExportURI();
        String schemeSpecificPart = dest.getSchemeSpecificPart();
        String calledHost = schemeSpecificPart.substring(schemeSpecificPart.indexOf("://")+3, schemeSpecificPart.lastIndexOf(":"));
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingAET(getAET(device)).calledAET(dest.toString())
                .calledHost(calledHost).outcome(null != ctx.getException() ? ctx.getException().getMessage() : null)
                .pID(getPID(xdsiManifest)).pName(pName(xdsiManifest)).submissionSetUID(ctx.getSubmissionSetUID()).build();
        obj.add(new AuditInfo(i));
        writeSpoolFile(String.valueOf(AuditServiceUtils.EventType.PROV_REGIS), obj);
    }

    private void auditProvideAndRegister(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType et) {
        AuditInfo ai = new AuditInfo(readerObj.getMainInfo());
        EventIdentification ei = getEI(et, ai.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant apSource = new BuildActiveParticipant.Builder(ai.getField(AuditInfo.CALLING_AET),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID()).requester(et.isSource).roleIDCode(et.source).build();
        BuildActiveParticipant apDest = new BuildActiveParticipant.Builder(ai.getField(AuditInfo.CALLED_AET),
                ai.getField(AuditInfo.CALLED_HOST)).requester(et.isDest).roleIDCode(et.destination).build();
        BuildParticipantObjectIdentification poiPatient = new BuildParticipantObjectIdentification.Builder(
                ai.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .build();
        BuildParticipantObjectIdentification poiSubmissionSet = new BuildParticipantObjectIdentification.Builder(
                ai.getField(AuditInfo.SUBMISSION_SET_UID), AuditMessages.ParticipantObjectIDTypeCode.IHE_XDS_METADATA,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Job)
                .build();
        emitAuditMessage(ei, getApList(apSource, apDest), getPoiList(poiPatient, poiSubmissionSet), auditLogger);
    }

    void spoolStgCmt(StgCmtEventInfo stgCmtEventInfo) {
        try {
            ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            String callingAET = stgCmtEventInfo.getRemoteAET() != null ? stgCmtEventInfo.getRemoteAET()
                    : stgCmtEventInfo.getRequest() != null && stgCmtEventInfo.getRequest().getAttribute(keycloakClassName) != null
                    ? getPreferredUsername(stgCmtEventInfo.getRequest()) : stgCmtEventInfo.getRequest().getRemoteHost();
            String calledAET = stgCmtEventInfo.getRequest() != null
                                ? stgCmtEventInfo.getRequest().getRequestURI()
                                : stgCmtEventInfo.getLocalAET();
            ApplicationEntity remoteAE = stgCmtEventInfo.getRemoteAET() != null
                    ? aeCache.findApplicationEntity(stgCmtEventInfo.getRemoteAET()) : null;
            String callingHost = remoteAE != null
                    ? remoteAE.getConnections().get(0).getHostname() : stgCmtEventInfo.getRequest().getRemoteHost();
            Attributes eventInfo = stgCmtEventInfo.getExtendedEventInfo();
            Sequence failed = eventInfo.getSequence(Tag.FailedSOPSequence);
            Sequence success = eventInfo.getSequence(Tag.ReferencedSOPSequence);
            String pID = eventInfo.getString(Tag.PatientID) != null ? getPID(eventInfo) : arcDev.auditUnknownPatientID();
            String studyUID = eventInfo.getStrings(Tag.StudyInstanceUID) != null
                    ? buildStrings(eventInfo.getStrings(Tag.StudyInstanceUID)) : arcDev.auditUnknownStudyInstanceUID();
            if (failed != null && !failed.isEmpty()) {
                Set<String> failureReasons = new HashSet<>();
                Set<AuditInfo> aiSet = new HashSet<>();
                LinkedHashSet<Object> objs = new LinkedHashSet<>();
                for (Attributes item : failed) {
                    BuildAuditInfo ii = new BuildAuditInfo.Builder()
                            .sopCUID(item.getString(Tag.ReferencedSOPClassUID))
                            .sopIUID(item.getString(Tag.ReferencedSOPInstanceUID)).build();
                    String outcome = item.getInt(Tag.FailureReason, 0) == Status.NoSuchObjectInstance
                            ? "NoSuchObjectInstance" : item.getInt(Tag.FailureReason, 0) == Status.ClassInstanceConflict
                            ? "ClassInstanceConflict" : "ProcessingFailure";
                    failureReasons.add(outcome);
                    aiSet.add(new AuditInfo(ii));
                }
                BuildAuditInfo i = new BuildAuditInfo.Builder().callingAET(callingAET).callingHost(callingHost)
                        .calledAET(calledAET).pID(pID).pName(pName(eventInfo)).studyUID(studyUID)
                        .outcome(buildStrings(failureReasons.toArray(new String[failureReasons.size()]))).build();
                objs.add(new AuditInfo(i));
                for (AuditInfo ai : aiSet)
                    objs.add(ai);
                writeSpoolFile(AuditServiceUtils.EventType.STG_CMT__E.toString(), objs);
            }
            if (success != null && !success.isEmpty()) {
                LinkedHashSet<Object> objs = new LinkedHashSet<>();
                BuildAuditInfo i = new BuildAuditInfo.Builder().callingAET(callingAET)
                        .callingHost(callingHost).calledAET(calledAET).pID(pID)
                        .pName(pName(eventInfo)).studyUID(studyUID).build();
                objs.add(new AuditInfo(i));
                for (Attributes item : success) {
                    BuildAuditInfo ii = new BuildAuditInfo.Builder().sopCUID(item.getString(Tag.ReferencedSOPClassUID))
                            .sopIUID(item.getString(Tag.ReferencedSOPInstanceUID)).build();
                    objs.add(new AuditInfo(ii));
                }
                writeSpoolFile(AuditServiceUtils.EventType.STG_CMT__P.toString(), objs);
            }
        } catch (ConfigurationException e) {
            LOG.error(e.getMessage(), stgCmtEventInfo.getRemoteAET());
        }
    }

    private void auditStorageCommit(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType et) {
        AuditInfo stgCmtI = new AuditInfo(readerObj.getMainInfo());
        EventIdentification ei = getEI(et, stgCmtI.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant apDest = new BuildActiveParticipant.Builder(stgCmtI.getField(AuditInfo.CALLED_AET),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID()).requester(et.isDest)
                .roleIDCode(et.destination).build();
        BuildActiveParticipant apSource = new BuildActiveParticipant.Builder(stgCmtI.getField(AuditInfo.CALLING_AET),
                stgCmtI.getField(AuditInfo.CALLING_HOST)).requester(et.isSource).roleIDCode(et.source).build();
        BuildParticipantObjectIdentification poiPat = new BuildParticipantObjectIdentification.Builder(
                stgCmtI.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(stgCmtI.getField(AuditInfo.P_NAME)).build();
        String[] studyUIDs = StringUtils.split(stgCmtI.getField(AuditInfo.STUDY_UID), ';');
        ParticipantObjectContainsStudy pocs = getPocs(studyUIDs);
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (String line : readerObj.getInstanceLines()) {
            AuditInfo ii = new AuditInfo(line);
            buildSOPClassMap(sopClassMap, ii.getField(AuditInfo.SOP_CUID), ii.getField(AuditInfo.SOP_IUID));
        }
        HashSet<SOPClass> sopC = new HashSet<>();
        if (studyUIDs.length>1 || stgCmtI.getField(AuditInfo.OUTCOME) != null)
            for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet())
                sopC.add(getSOPC(entry.getValue(), entry.getKey(), entry.getValue().size()));
        else
            for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet())
                sopC.add(getSOPC(null, entry.getKey(), entry.getValue().size()));
        BuildParticipantObjectDescription poDesc = new BuildParticipantObjectDescription.Builder(sopC, pocs).build();
        BuildParticipantObjectIdentification poiStudy = new BuildParticipantObjectIdentification.Builder(studyUIDs[0],
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(poDesc)).lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.Verification).build();
        emitAuditMessage(ei, getApList(apSource, apDest), getPoiList(poiStudy, poiPat), auditLogger);
    }

    private String buildStrings(String[] strings) {
        StringBuilder b = new StringBuilder();
        b.append(strings[0]);
        for (int i = 1; i < strings.length; i++)
            b.append(';').append(strings[i]);
        return b.toString();
    }

    private BuildAuditInfo getAIStoreCtx(StoreContext ctx) {
        StoreSession ss = ctx.getStoreSession();
        HttpServletRequest req = ss.getHttpRequest();
        Attributes attr = ctx.getAttributes();
        String callingHost = ss.getRemoteHostName();
        String callingAET = ss.getCallingAET() != null ? ss.getCallingAET()
                : req != null && req.getAttribute(keycloakClassName) != null
                ? getPreferredUsername(req) : callingHost;
        if (callingAET == null && callingHost == null)
            callingAET = ss.toString();
        String outcome = null != ctx.getException() ? null != ctx.getRejectionNote()
                ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() + " - " + ctx.getException().getMessage()
                : getOD(ctx.getException()) : null;
        String warning = ctx.getException() == null && null != ctx.getRejectionNote()
                ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() : null;
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(callingHost).callingAET(callingAET)
                .calledAET(req != null ? req.getRequestURI() : ss.getCalledAET()).studyUID(ctx.getStudyInstanceUID())
                .accNum(getAcc(attr)).pID(getPID(attr)).pName(pName(attr))
                .outcome(outcome).warning(warning).studyDate(getSD(attr)).build();
        return i;
    }

    private String getFileName(AuditServiceUtils.EventType et, String callingAET, String calledAET, String studyIUID) {
        return String.valueOf(et) + '-' + callingAET + '-' + calledAET + '-' + studyIUID;
    }

    private String getFailOutcomeDesc(RetrieveContext ctx) {
        return null != ctx.getException()
                ? ctx.getException().getMessage() != null ? ctx.getException().getMessage() : ctx.getException().toString()
                : (ctx.failedSOPInstanceUIDs().length > 0 && (ctx.completed() == 0 && ctx.warning() == 0))
                ? "Unable to perform sub-operations on all instances"
                : (ctx.failedSOPInstanceUIDs().length > 0 && !(ctx.completed() == 0 && ctx.warning() == 0))
                ? "Retrieve of " + ctx.failed() + " objects failed" : null;
    }

    private String getSD(Attributes attr) {
        return attr != null ? attr.getString(Tag.StudyDate) : null;
    }

    private String getAcc(Attributes attr) {
        return attr != null ? attr.getString(Tag.AccessionNumber) : null;
    }

    private String sopCUID(Attributes attrs) {
        return attrs != null ? attrs.getString(Tag.SOPClassUID) : null;
    }

    private String getPreferredUsername(HttpServletRequest req) {
        RefreshableKeycloakSecurityContext securityContext = (RefreshableKeycloakSecurityContext)
                req.getAttribute(KeycloakSecurityContext.class.getName());
        return securityContext.getToken().getPreferredUsername();
    }

    private String getPID(Attributes attrs) {
        return attrs != null
                ? attrs.getString(Tag.PatientID) != null
                ? getPlainOrHashedPatientID(IDWithIssuer.pidOf(attrs).toString())
                : device.getDeviceExtension(ArchiveDeviceExtension.class).auditUnknownPatientID() : null;
    }

    private String pName(Attributes attr) {
        if (attr != null && attr.getString(Tag.PatientName) != null) {
            ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            ShowPatientInfo showPatientInfo = arcDev.showPatientInfoInAuditLog();
            StringBuilder sb = new StringBuilder(256);
            if (showPatientInfo != ShowPatientInfo.PLAIN_TEXT)
                sb.append(attr.getString(Tag.PatientName).hashCode());
            else
                sb.append(attr.getString(Tag.PatientName));
            return sb.toString();
        }
        return null;
    }

    private String getPlainOrHashedPatientID(String pID) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ShowPatientInfo showPatientInfo = arcDev.showPatientInfoInAuditLog();
        StringBuilder sb = new StringBuilder(256);
        if (showPatientInfo == ShowPatientInfo.HASH_NAME_AND_ID)
            sb.append(pID.hashCode());
        else
            sb.append(pID);
        return sb.toString();
    }

    private String getOD(Exception e) {
        return e != null ? e.getMessage() : null;
    }

    private ParticipantObjectDetail getPod(String type, String value) {
        return value != null ? AuditMessages.createParticipantObjectDetail(type, value.getBytes()) : null;
    }

    private ParticipantObjectContainsStudy getPocs(String... studyUIDs) {
        return AuditMessages.getPocs(studyUIDs);
    }

    private ParticipantObjectDescription getPODesc(BuildParticipantObjectDescription desc) {
        return AuditMessages.getPODesc(desc);
    }

    private HashSet<Accession> getAccessions(String accNum) {
        return AuditMessages.getAccessions(accNum);
    }

    private HashSet<SOPClass> getSopClasses(HashSet<String> instanceLines) {
        HashSet<SOPClass> sopC = new HashSet<>();
        for (String line : instanceLines) {
            AuditInfo ii = new AuditInfo(line);
            sopC.add(getSOPC(null, ii.getField(AuditInfo.SOP_CUID),
                    Integer.parseInt(ii.getField(AuditInfo.SOP_IUID))));
        }
        return sopC;
    }

    private SOPClass getSOPC(HashSet<String> instances, String uid, Integer numI) {
        return AuditMessages.getSOPC(instances, uid, numI);
    }

    private Calendar getEventTime(Path path, AuditLogger auditLogger){
        Calendar eventTime = auditLogger.timeStamp();
        try {
            eventTime.setTimeInMillis(Files.getLastModifiedTime(path).toMillis());
        } catch (Exception e) {
            LOG.warn("Failed to get Last Modified Time of Audit Spool File - {} ", auditLogger.getCommonName(), path, e);
        }
        return eventTime;
    }

    private String getAET(Device device) {
        return AuditMessages.getAET(
                device.getApplicationAETitles().toArray(new String[device.getApplicationAETitles().size()]));
    }

    private String getLocalHostName(AuditLogger log) {
        return log.getConnections().get(0).getHostname();
    }

    private void writeSpoolFile(String eventType, LinkedHashSet<Object> obj) {
        if (obj.isEmpty()) {
            LOG.warn("Attempt to write empty file : " + eventType);
            return;
        }
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = Paths.get(StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()),
                        auditLogger.getCommonName().replaceAll(" ", "_"));
                try {
                    Files.createDirectories(dir);
                    Path file = Files.createTempFile(dir, eventType, null);
                    try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            StandardOpenOption.APPEND))) {
                        for (Object o : obj)
                            writer.writeLine(o);
                    }
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File - {} ", auditLogger.getCommonName(), e);
                }
            }
        }
    }

    private void writeSpoolFileStoreOrWadoRetrieve(String fileName, Object patStudyInfo, Object instanceInfo) {
        if (patStudyInfo == null && instanceInfo == null) {
            LOG.warn("Attempt to write empty file : " + fileName);
            return;
        }
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = Paths.get(StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()),
                        auditLogger.getCommonName().replaceAll(" ", "_"));
                Path file = dir.resolve(fileName);
                boolean append = Files.exists(file);
                try {
                    if (!append)
                        Files.createDirectories(dir);
                    try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW))) {
                        if (!append) {
                            writer.writeLine(patStudyInfo);
                        }
                        writer.writeLine(instanceInfo);
                    }
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File - {} ", auditLogger.getCommonName(), file, e);
                }
            }
        }
    }

    private LinkedHashSet<Object> getDeletionObjsForSpooling(HashMap<String, HashSet<String>> sopClassMap,
                                                             AuditInfo i) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        obj.add(i);
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet()) {
            obj.add(new AuditInfo(new BuildAuditInfo.Builder().sopCUID(entry.getKey())
                    .sopIUID(String.valueOf(entry.getValue().size())).build()));
        }
        return obj;
    }

    private List<ActiveParticipant> getApList(BuildActiveParticipant... aps) {
        return AuditMessages.getApList(aps);
    }

    private List<ParticipantObjectIdentification> getPoiList(BuildParticipantObjectIdentification... pois) {
        return AuditMessages.getPoiList(pois);
    }

    private void emitAuditMessage(EventIdentification ei, List<ActiveParticipant> apList,
                                  List<ParticipantObjectIdentification> poiList, AuditLogger log) {
        AuditMessage msg = AuditMessages.createMessage(ei, apList, poiList);
        msg.getAuditSourceIdentification().add(log.createAuditSourceIdentification());
        try {
            log.write(log.timeStamp(), msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", log.getCommonName(), e);
        }
    }

    private String getEOI(String outcomeDesc) {
        return outcomeDesc != null ? AuditMessages.EventOutcomeIndicator.MinorFailure : AuditMessages.EventOutcomeIndicator.Success;
    }

    private EventIdentification getEI(AuditServiceUtils.EventType et, String desc, Calendar t) {
        BuildEventIdentification ei =  new BuildEventIdentification.Builder(
                    et.eventID, et.eventActionCode, t, getEOI(desc)).outcomeDesc(desc).eventTypeCode(et.eventTypeCode).build();
        return AuditMessages.getEI(ei);
    }

    private EventIdentification getCustomEI(AuditServiceUtils.EventType et, String failureDesc, String warningDesc, Calendar t) {
        if (failureDesc != null)
            return getEI(et, failureDesc, t);
        else {
            BuildEventIdentification ei = new BuildEventIdentification.Builder(
                    et.eventID, et.eventActionCode, t, AuditMessages.EventOutcomeIndicator.Success)
                    .outcomeDesc(warningDesc).build();
            return AuditMessages.getEI(ei);
        }
    }
}
