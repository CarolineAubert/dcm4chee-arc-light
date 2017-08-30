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
import org.dcm4chee.arc.ArchiveServiceEvent;
import org.dcm4chee.arc.ConnectionEvent;
import org.dcm4chee.arc.keycloak.KeycloakUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.RejectionState;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.event.RejectionNoteSent;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.HttpServletRequestInfo;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.stgcmt.StgCmtEventInfo;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
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
    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    private void aggregateAuditMessage(AuditLogger auditLogger, Path path) throws IOException {
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.fromFile(path);
        if (path.toFile().length() == 0)
            throw new IOException("Attempt to read from an empty file. ");
        switch (eventType.eventClass) {
            case APPLN_ACTIVITY:
                auditApplicationActivity(auditLogger, path, eventType);
                break;
            case CONN_REJECT:
                auditConnectionRejected(auditLogger, path, eventType);
                break;
            case STORE_WADOR:
                auditStoreOrWADORetrieve(auditLogger, path, eventType);
                break;
            case RETRIEVE:
                auditRetrieve(auditLogger, path, eventType);
                break;
            case USER_DELETED:
            case SCHEDULER_DELETED:
                auditDeletion(auditLogger, path, eventType);
                break;
            case QUERY:
                auditQuery(auditLogger, path, eventType);
                break;
            case HL7:
                auditPatientRecord(auditLogger, path, eventType);
                break;
            case PROC_STUDY:
                auditProcedureRecord(auditLogger, path, eventType);
                break;
            case PROV_REGISTER:
                auditProvideAndRegister(auditLogger, path, eventType);
                break;
            case STGCMT:
                auditStorageCommit(auditLogger, path, eventType);
                break;
            case INST_RETRIEVED:
                auditExternalRetrieve(auditLogger, path, eventType);
                break;
        }
    }

    void spoolApplicationActivity(ArchiveServiceEvent event) {
        if (event.getType() == ArchiveServiceEvent.Type.RELOADED)
            return;

        HttpServletRequest req = event.getRequest();
        BuildAuditInfo info = req != null
                              ? restfulTriggeredApplicationActivityInfo(req)
                              : systemTriggeredApplicationActivityInfo();
        writeSpoolFile(AuditServiceUtils.EventType.forApplicationActivity(event), info);
    }

    private BuildAuditInfo systemTriggeredApplicationActivityInfo() {
        return new BuildAuditInfo.Builder().calledAET(getAET()).build();
    }

    private BuildAuditInfo restfulTriggeredApplicationActivityInfo(HttpServletRequest req) {
        return new BuildAuditInfo.Builder()
                .calledAET(getAET())
                .callingAET(KeycloakUtils.getUserName(req))
                .callingHost(req.getRemoteAddr())
                .build();
    }

    private void auditApplicationActivity(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        EventIdentificationBuilder ei = toBuildEventIdentification(eventType, null, getEventTime(path, auditLogger));
        AuditInfo archiveInfo = new AuditInfo(reader.getMainInfo());
        ActiveParticipantBuilder[] activeParticipantBuilder = buildApplicationActivityActiveParticipants(auditLogger, eventType, archiveInfo);
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder);
    }

    private ActiveParticipantBuilder[] buildApplicationActivityActiveParticipants(
            AuditLogger auditLogger, AuditServiceUtils.EventType eventType, AuditInfo archiveInfo) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                archiveInfo.getField(AuditInfo.CALLED_AET),
                                getLocalHostName(auditLogger))
                                .altUserID(AuditLogger.processID())
                                .roleIDCode(eventType.destination)
                                .build();
        if (isServiceUserTriggered(archiveInfo.getField(AuditInfo.CALLING_AET)))
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                    archiveInfo.getField(AuditInfo.CALLING_AET),
                                    archiveInfo.getField(AuditInfo.CALLING_HOST)).
                                    requester(true)
                                    .roleIDCode(eventType.source)
                                    .build();
        return activeParticipantBuilder;
    }

    void spoolInstancesDeleted(StoreContext ctx) {
        if (isExternalRejectionSourceDestSame(ctx))
            return;
        Attributes attrs = ctx.getAttributes();
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (Attributes studyRef : attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence))
            for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence))
                for (Attributes sopRef : seriesRef.getSequence(Tag.ReferencedSOPSequence))
                    buildSOPClassMap(sopClassMap, sopRef.getString(Tag.ReferencedSOPClassUID),
                            sopRef.getString(Tag.ReferencedSOPInstanceUID));
        LinkedHashSet<Object> deleteObjs = getDeletionObjsForSpooling(sopClassMap, new AuditInfo(getAIStoreCtx(ctx)));
        AuditServiceUtils.EventType eventType = ctx.getStoredInstance().getSeries().getStudy().getRejectionState()== RejectionState.COMPLETE
                                                    ? AuditServiceUtils.EventType.RJ_COMPLET
                                                    : AuditServiceUtils.EventType.RJ_PARTIAL;
        writeSpoolFile(eventType, deleteObjs);
    }

    private boolean isExternalRejectionSourceDestSame(StoreContext ctx) {
        StoreSession ss = ctx.getStoreSession();
        return ctx.getRejectionNote() != null && ss.getHttpRequest() == null && ss.getCallingAET().equals(ss.getCalledAET());
    }

    void spoolStudyDeleted(StudyDeleteContext ctx) {
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (org.dcm4chee.arc.entity.Instance i : ctx.getInstances())
            buildSOPClassMap(sopClassMap, i.getSopClassUID(), i.getSopInstanceUID());
        HttpServletRequest request = ctx.getHttpRequest();
        BuildAuditInfo i = request != null ? buildPermDeletionAuditInfoForWeb(request, ctx)
                : buildPermDeletionAuditInfoForScheduler(ctx);
        AuditServiceUtils.EventType eventType = request != null
                                                ? AuditServiceUtils.EventType.PRMDLT_WEB
                                                : AuditServiceUtils.EventType.PRMDLT_SCH;
        LinkedHashSet<Object> deleteObjs = getDeletionObjsForSpooling(sopClassMap, new AuditInfo(i));
        writeSpoolFile(eventType, deleteObjs);
    }

    void spoolExternalRejection(RejectionNoteSent rejectionNoteSent) throws ConfigurationException {
        LinkedHashSet<Object> deleteObjs = new LinkedHashSet<>();
        Attributes attrs = rejectionNoteSent.getRejectionNote();
        Attributes codeItem = attrs.getSequence(Tag.ConceptNameCodeSequence).get(0);
        Code code = new Code(codeItem.getString(Tag.CodeValue), codeItem.getString(Tag.CodingSchemeDesignator), null, "?");
        RejectionNote rjNote = getArchiveDevice().getRejectionNote(code);
        HttpServletRequest req = rejectionNoteSent.getRequest();
        String callingAET = req != null
                ? KeycloakUtils.getUserName(req)
                : rejectionNoteSent.getLocalAET();
        String calledAET = req != null
                ? req.getRequestURI() : rejectionNoteSent.getRemoteAET();
        String callingHost = req != null
                ? req.getRemoteHost() : toHost(rejectionNoteSent.getLocalAET());
        deleteObjs.add(new AuditInfo(new BuildAuditInfo.Builder()
                .callingAET(callingAET)
                .callingHost(callingHost)
                .calledAET(calledAET)
                .calledHost(toHost(rejectionNoteSent.getRemoteAET()))
                .outcome(String.valueOf(rjNote.getRejectionNoteType()))
                .studyUIDAccNumDate(attrs)
                .pIDAndName(attrs, getArchiveDevice())
                .build()));
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (Attributes studyRef : attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence))
            for (Attributes refSer : studyRef.getSequence(Tag.ReferencedSeriesSequence))
                for (Attributes refSop : refSer.getSequence(Tag.ReferencedSOPSequence))
                    buildSOPClassMap(sopClassMap, refSop.getString(Tag.ReferencedSOPClassUID),
                            refSop.getString(Tag.ReferencedSOPInstanceUID));
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet()) {
            deleteObjs.add(new AuditInfo(new BuildAuditInfo.Builder().sopCUID(entry.getKey())
                    .sopIUID(String.valueOf(entry.getValue().size())).build()));
        }
        AuditServiceUtils.EventType clientET = rejectionNoteSent.isStudyDeleted()
                ? AuditServiceUtils.EventType.PRMDLT_WEB
                : AuditServiceUtils.EventType.RJ_PARTIAL;
        writeSpoolFile(clientET, deleteObjs);
        if (rejectionNoteSent.getLocalAET().equals(rejectionNoteSent.getRemoteAET())) {
            AuditServiceUtils.EventType serverET = rejectionNoteSent.isStudyDeleted()
                    ? AuditServiceUtils.EventType.RJ_COMPLET
                    : AuditServiceUtils.EventType.RJ_PARTIAL;
            writeSpoolFile(serverET, deleteObjs);
        }
    }
    private String toHost(String aet) throws ConfigurationException {
        ApplicationEntity ae = aeCache.findApplicationEntity(aet);
        StringBuilder b = new StringBuilder();
        if (ae != null) {
            List<Connection> conns = ae.getConnections();
            b.append(conns.get(0).getHostname());
            for (int i = 1; i < conns.size(); i++)
                b.append(';').append(conns.get(i).getHostname());
        }
        return b.toString();
    }


    private BuildAuditInfo buildPermDeletionAuditInfoForWeb(HttpServletRequest req, StudyDeleteContext ctx) {
        return new BuildAuditInfo.Builder()
                .callingAET(KeycloakUtils.getUserName(req))
                .callingHost(req.getRemoteHost())
                .calledAET(req.getRequestURI())
                .studyUIDAccNumDate(ctx.getStudy().getAttributes())
                .pIDAndName(ctx.getPatient().getAttributes(), getArchiveDevice())
                .outcome(getOD(ctx.getException()))
                .build();
    }

    private BuildAuditInfo buildPermDeletionAuditInfoForScheduler(StudyDeleteContext ctx) {
        return new BuildAuditInfo.Builder()
                .studyUIDAccNumDate(ctx.getStudy().getAttributes())
                .pIDAndName(ctx.getPatient().getAttributes(), getArchiveDevice())
                .outcome(getOD(ctx.getException()))
                .build();
    }

    private void auditDeletion(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType) throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        boolean userDeleted = eventType.eventClass == AuditServiceUtils.EventClass.USER_DELETED;
        EventIdentificationBuilder ei = toCustomBuildEventIdentification(eventType, auditInfo.getField(AuditInfo.OUTCOME),
                auditInfo.getField(AuditInfo.WARNING), getEventTime(path, auditLogger));
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        if (userDeleted) {
            activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                    auditInfo.getField(AuditInfo.CALLING_AET), auditInfo.getField(AuditInfo.CALLING_HOST))
                    .requester(true).build();
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                    auditInfo.getField(AuditInfo.CALLED_AET),
                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                    .build();
        } else
            activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                    getAET(),
                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                    .requester(true).build();

        
        ParticipantObjectDescriptionBuilder desc = new ParticipantObjectDescriptionBuilder.Builder()
                .sopC(sopClasses(reader.getInstanceLines()))
                .acc(auditInfo.getField(AuditInfo.ACC_NUM)).build();
        
        ParticipantObjectIdentificationBuilder poiStudy = new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.STUDY_UID), 
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(desc)
                .detail(getPod(studyDate, auditInfo.getField(AuditInfo.STUDY_DATE)))
                .build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poiStudy, patientPOI(auditInfo));
    }

    void spoolExternalRetrieve(ExternalRetrieveContext ctx) {
        String outcome = ctx.getResponse().getString(Tag.ErrorComment) != null
                            ? ctx.getResponse().getString(Tag.ErrorComment) + ctx.failed()
                            : null;
        String warning = ctx.warning() > 0
                            ? "Number Of Warning Sub operations" + ctx.warning()
                            : null;
        BuildAuditInfo info = new BuildAuditInfo.Builder()
                                .callingAET(ctx.getRequesterUserID())
                                .callingHost(ctx.getRequesterHostName())
                                .calledHost(ctx.getRemoteHostName())
                                .calledAET(ctx.getRemoteAET())
                                .moveAET(ctx.getRequestURI())
                                .destAET(ctx.getDestinationAET())
                                .warning(warning)
                                .studyUIDAccNumDate(ctx.getKeys())
                                .outcome(outcome)
                                .build();
        writeSpoolFile(AuditServiceUtils.EventType.INST_RETRV, info);
    }

    private void auditExternalRetrieve(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo i = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toCustomBuildEventIdentification(eventType, i.getField(AuditInfo.OUTCOME),
                i.getField(AuditInfo.WARNING), getEventTime(path, auditLogger));
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[4];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                i.getField(AuditInfo.CALLING_AET),
                                i.getField(AuditInfo.CALLING_HOST))
                                .requester(true)
                                .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                i.getField(AuditInfo.MOVEAET),
                                getLocalHostName(auditLogger))
                                .altUserID(AuditLogger.processID())
                                .build();
        activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                                i.getField(AuditInfo.CALLED_AET),
                                i.getField(AuditInfo.CALLED_HOST))
                                .roleIDCode(eventType.source)
                                .build();
        activeParticipantBuilder[3] = new ActiveParticipantBuilder.Builder(
                                i.getField(AuditInfo.DEST_AET),
                                i.getField(AuditInfo.DEST_NAP_ID))
                                .roleIDCode(eventType.destination)
                                .build();
        ParticipantObjectIdentificationBuilder studyPOI = new ParticipantObjectIdentificationBuilder.Builder(
                                                            i.getField(AuditInfo.STUDY_UID),
                                                            AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                                                            AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                                            AuditMessages.ParticipantObjectTypeCodeRole.Report)
                                                            .build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, studyPOI);
    }

    void spoolConnectionRejected(ConnectionEvent event) {
        BuildAuditInfo info = new BuildAuditInfo.Builder()
                            .callingHost(event.getSocket().getRemoteSocketAddress().toString())
                            .calledHost(event.getConnection().getHostname())
                            .outcome(event.getException().getMessage())
                            .build();
        writeSpoolFile(AuditServiceUtils.EventType.CONN__RJCT, info);
    }

    private void auditConnectionRejected(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo crI = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toBuildEventIdentification(eventType, crI.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                getAET(),
                                crI.getField(AuditInfo.CALLED_HOST))
                                .altUserID(AuditLogger.processID())
                                .build();
        String userID, napID;
        userID = napID = crI.getField(AuditInfo.CALLING_HOST);
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(userID, napID).requester(true).build();

        ParticipantObjectIdentificationBuilder poi = new ParticipantObjectIdentificationBuilder.Builder(
                                                    crI.getField(AuditInfo.CALLING_HOST),
                                                    AuditMessages.ParticipantObjectIDTypeCode.NodeID,
                                                    AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                                    null)
                                                    .build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poi);
    }

    void spoolQuery(QueryContext ctx) {
        boolean auditAggregate = getArchiveDevice().isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forQuery(ctx);
        AuditInfo auditInfo = ctx.getHttpRequest() != null ? createAuditInfoForQIDO(ctx) : createAuditInfoForFIND(ctx);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (!isSpoolingSuppressed(eventType, ctx.getCallingAET(), auditLogger)) {
                Path directory = toDirPath(auditLogger);
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
                        .callingAET(KeycloakUtils.getUserName(ctx.getHttpRequest()))
                        .calledAET(httpRequest.getRequestURI())
                        .queryPOID(ctx.getSearchMethod())
                        .queryString(httpRequest.getRequestURI() + httpRequest.getQueryString())
                        .build());
    }

    private boolean isSpoolingSuppressed(AuditServiceUtils.EventType eventType, String userID, AuditLogger auditLogger) {
        return !auditLogger.isInstalled()
                || (!auditLogger.getAuditSuppressCriteriaList().isEmpty()
                    && auditLogger.isAuditMessageSuppressed(createMinimalAuditMsg(eventType, userID)));
    }

    private AuditMessage createMinimalAuditMsg(AuditServiceUtils.EventType eventType, String userID) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(
                AuditMessages.toEventIdentification(toBuildEventIdentification(eventType, null, null)));
        ActiveParticipant ap = new ActiveParticipant();
        ap.setUserID(userID);
        ap.setUserIsRequestor(true);
        msg.getActiveParticipant().add(ap);
        return msg;
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

    private void auditQuery(
            AuditLogger auditLogger, Path file, AuditServiceUtils.EventType eventType) throws IOException {
        AuditInfo qrI;
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        EventIdentificationBuilder ei = toBuildEventIdentification(eventType, null, getEventTime(file, auditLogger));
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            qrI = new AuditInfo(new DataInputStream(in).readUTF());
            activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                    qrI.getField(AuditInfo.CALLING_AET),
                                    qrI.getField(AuditInfo.CALLING_HOST))
                                    .requester(true)
                                    .roleIDCode(eventType.source)
                                    .build();
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                    qrI.getField(AuditInfo.CALLED_AET),
                                    getLocalHostName(auditLogger))
                                    .altUserID(AuditLogger.processID())
                                    .roleIDCode(eventType.destination)
                                    .build();
            ParticipantObjectIdentificationBuilder poi;
            if (eventType == AuditServiceUtils.EventType.QUERY_QIDO) {
                poi = new ParticipantObjectIdentificationBuilder.Builder(
                        qrI.getField(AuditInfo.Q_POID),
                        AuditMessages.ParticipantObjectIDTypeCode.QIDO_QUERY,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Query)
                        .query(qrI.getField(AuditInfo.Q_STRING).getBytes())
                        .detail(getPod("QueryEncoding", String.valueOf(StandardCharsets.UTF_8)))
                        .build();
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
                poi = new ParticipantObjectIdentificationBuilder.Builder(
                        qrI.getField(AuditInfo.Q_POID),
                        AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Report)
                        .query(data)
                        .detail(getPod("TransferSyntax", UID.ImplicitVRLittleEndian))
                        .build();
            }
            emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poi);
        }
    }

    void spoolInstanceStored(StoreContext ctx) {
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forInstanceStored(ctx);
        if (eventType == null)
            return; // no audit message for duplicate received instance
        String callingAET = ctx.getStoreSession().getHttpRequest() != null
                ? ctx.getStoreSession().getHttpRequest().getRemoteAddr() : ctx.getStoreSession().getCallingAET().replace('|', '-');
        String fileName = getFileName(eventType, callingAET, ctx.getStoreSession().getCalledAET(), ctx.getStudyInstanceUID());
        BuildAuditInfo info = getAIStoreCtx(ctx);
        BuildAuditInfo instanceInfo = new BuildAuditInfo.Builder()
                                    .sopCUID(ctx.getSopClassUID()).sopIUID(ctx.getSopInstanceUID())
                                    .mppsUID(ctx.getMppsInstanceUID())
                                    .build();
        writeSpoolFileStoreOrWadoRetrieve(fileName, info, instanceInfo);
    }
    
    void spoolRetrieveWADO(RetrieveContext ctx) {
        HttpServletRequestInfo req = ctx.getHttpServletRequestInfo();
        Collection<InstanceLocations> il = ctx.getMatches();
        Attributes attrs = new Attributes();
        for (InstanceLocations i : il)
            attrs = i.getAttributes();
        String fileName = getFileName(AuditServiceUtils.EventType.WADO___URI, req.requesterHost,
                ctx.getLocalAETitle(), ctx.getStudyInstanceUIDs()[0]);
        BuildAuditInfo info = new BuildAuditInfo.Builder()
                                .callingHost(req.requesterHost)
                                .callingAET(req.requesterUserID)
                                .calledAET(req.requestURI)
                                .studyUIDAccNumDate(attrs)
                                .pIDAndName(attrs, getArchiveDevice())
                                .outcome(null != ctx.getException() ? ctx.getException().getMessage() : null)
                                .build();
        BuildAuditInfo instanceInfo = new BuildAuditInfo.Builder()
                                        .sopCUID(attrs.getString(Tag.SOPClassUID))
                                        .sopIUID(ctx.getSopInstanceUIDs()[0])
                                        .build();
        writeSpoolFileStoreOrWadoRetrieve(fileName, info, instanceInfo);
    }

    private void buildSOPClassMap(HashMap<String, HashSet<String>> sopClassMap, String cuid, String iuid) {
        HashSet<String> iuids = sopClassMap.get(cuid);
        if (iuids == null) {
            iuids = new HashSet<>();
            sopClassMap.put(cuid, iuids);
        }
        iuids.add(iuid);
    }

    private void auditStoreOrWADORetrieve(AuditLogger auditLogger, Path path,
                                          AuditServiceUtils.EventType eventType) throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        List<String> mppsUIDs = new ArrayList<>();
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        for (String line : reader.getInstanceLines()) {
            AuditInfo iI = new AuditInfo(line);
            buildSOPClassMap(sopClassMap, iI.getField(AuditInfo.SOP_CUID), iI.getField(AuditInfo.SOP_IUID));
            String mppsUID = iI.getField(AuditInfo.MPPS_UID);
            if (mppsUID != null)
                mppsUIDs.add(mppsUID);
        }

        EventIdentificationBuilder ei = toBuildEventIdentification(eventType, auditInfo.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));

        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                auditInfo.getField(AuditInfo.CALLING_AET),
                                auditInfo.getField(AuditInfo.CALLING_HOST))
                                .requester(true)
                                .roleIDCode(eventType.source)
                                .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                auditInfo.getField(AuditInfo.CALLED_AET),
                                getLocalHostName(auditLogger))
                                .altUserID(AuditLogger.processID())
                                .roleIDCode(eventType.destination)
                                .build();

        SOPClass[] sopC = new SOPClass[sopClassMap.size()];
        int i = 0;
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet()) {
            sopC[i] = AuditMessages.createSOPClass(null, entry.getKey(), entry.getValue().size());
            i++;
        }

        ParticipantObjectDescriptionBuilder desc = new ParticipantObjectDescriptionBuilder.Builder()
                                                .sopC(sopC)
                                                .acc(auditInfo.getField(AuditInfo.ACC_NUM))
                                                .mpps(mppsUIDs.toArray(new String[mppsUIDs.size()]))
                                                .build();

        String lifecycle = (eventType == AuditServiceUtils.EventType.STORE_CREA
                || eventType == AuditServiceUtils.EventType.STORE_UPDT)
                ? AuditMessages.ParticipantObjectDataLifeCycle.OriginationCreation : null;
        ParticipantObjectIdentificationBuilder poiStudy = new ParticipantObjectIdentificationBuilder.Builder(
                                                        auditInfo.getField(AuditInfo.STUDY_UID),
                                                        AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                                                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                                        AuditMessages.ParticipantObjectTypeCodeRole.Report)
                                                        .desc(desc)
                                                        .detail(getPod(studyDate, auditInfo.getField(AuditInfo.STUDY_DATE)))
                                                        .lifeCycle(lifecycle)
                                                        .build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poiStudy, patientPOI(auditInfo));
    }

    void spoolRetrieve(AuditServiceUtils.EventType eventType, RetrieveContext ctx) {
        RetrieveContextAuditInfoBuilder builder = new RetrieveContextAuditInfoBuilder(ctx, getArchiveDevice(), eventType);
        for (BuildAuditInfo[] buildAuditInfos : builder.getBuildAuditInfos())
            writeSpoolFile(builder.getEventType(), buildAuditInfos);
    }

    private ArchiveDeviceExtension getArchiveDevice() {
        return device.getDeviceExtension(ArchiveDeviceExtension.class);
    }

    private void auditRetrieve(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo ri = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toCustomBuildEventIdentification(eventType, ri.getField(AuditInfo.OUTCOME),
                ri.getField(AuditInfo.WARNING), getEventTime(path, auditLogger));

        HashMap<String, AccessionNumSopClassInfo> study_accNumSOPClassInfo = new HashMap<>();
        String pID = getArchiveDevice().auditUnknownPatientID();
        String pName = null;
        String studyDt = null;
        for (String line : reader.getInstanceLines()) {
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
        List<ParticipantObjectIdentificationBuilder> pois = new ArrayList<>();
        for (Map.Entry<String, AccessionNumSopClassInfo> entry : study_accNumSOPClassInfo.entrySet()) {
            HashSet<SOPClass> sopC = new HashSet<>();
            for (Map.Entry<String, HashSet<String>> sopClassMap : entry.getValue().getSopClassMap().entrySet())
                if (ri.getField(AuditInfo.FAILED_IUID_SHOW) != null)
                    sopC.add(getSOPC(sopClassMap.getValue(), sopClassMap.getKey(), sopClassMap.getValue().size()));
                else
                    sopC.add(getSOPC(null, sopClassMap.getKey(), sopClassMap.getValue().size()));

            ParticipantObjectDescriptionBuilder desc = new ParticipantObjectDescriptionBuilder.Builder()
                                                        .sopC(sopC.toArray(new SOPClass[sopC.size()]))
                                                        .acc(entry.getValue().getAccNum())
                                                        .build();
            ParticipantObjectIdentificationBuilder poi = new ParticipantObjectIdentificationBuilder.Builder(
                                                        entry.getKey(),
                                                        AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                                                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                                        AuditMessages.ParticipantObjectTypeCodeRole.Report)
                                                        .desc(desc)
                                                        .detail(getPod(studyDate, studyDt))
                                                        .build();
            pois.add(poi);
        }
        ParticipantObjectIdentificationBuilder poiPatient = new ParticipantObjectIdentificationBuilder.Builder(
                                                            pID,
                                                            AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                                                            AuditMessages.ParticipantObjectTypeCode.Person,
                                                            AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                                                            .name(pName)
                                                            .build();
        pois.add(poiPatient);
        emitAuditMessage(auditLogger, ei,
                        getApsForRetrieve(eventType, ri, auditLogger),
                        pois.toArray(new ParticipantObjectIdentificationBuilder[pois.size()]));
    }

    private ActiveParticipantBuilder[] getApsForRetrieve(AuditServiceUtils.EventType eventType, AuditInfo ri, AuditLogger auditLogger) {
        return ri.getField(AuditInfo.MOVEAET) != null
                ? getApsForMove(eventType, ri, auditLogger)
                : ri.getField(AuditInfo.IS_EXPORT) != null
                    ? getApsForExport(eventType, ri, auditLogger)
                    : getApsForGetOrWadoRS(eventType, ri, auditLogger);
    }

    private ActiveParticipantBuilder[] getApsForMove(AuditServiceUtils.EventType eventType, AuditInfo ri, AuditLogger auditLogger) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                ri.getField(AuditInfo.CALLED_AET),
                                getLocalHostName(auditLogger))
                                .altUserID(AuditLogger.processID())
                                .roleIDCode(eventType.source)
                                .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                ri.getField(AuditInfo.DEST_AET),
                                ri.getField(AuditInfo.DEST_NAP_ID))
                                .roleIDCode(eventType.destination)
                                .build();
        activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                                ri.getField(AuditInfo.MOVEAET),
                                ri.getField(AuditInfo.CALLING_HOST))
                                .requester(true)
                                .build();
        return activeParticipantBuilder;
    }

    private ActiveParticipantBuilder[] getApsForExport(AuditServiceUtils.EventType eventType, AuditInfo ri, AuditLogger auditLogger) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(ri.getField(AuditInfo.DEST_AET),
                ri.getField(AuditInfo.DEST_NAP_ID)).roleIDCode(eventType.destination).build();
        if (ri.getField(AuditInfo.CALLING_AET) == null) {
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                    ri.getField(AuditInfo.CALLED_AET),
                                    getLocalHostName(auditLogger))
                                    .altUserID(AuditLogger.processID())
                                    .requester(true)
                                    .roleIDCode(eventType.source)
                                    .build();
        }
        else {
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                    ri.getField(AuditInfo.CALLED_AET),
                                    getLocalHostName(auditLogger))
                                    .altUserID(AuditLogger.processID())
                                    .roleIDCode(eventType.source)
                                    .build();
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                                    ri.getField(AuditInfo.CALLING_AET),
                                    ri.getField(AuditInfo.CALLING_HOST))
                                    .requester(true)
                                    .build();
        }
        return activeParticipantBuilder;
    }

    private ActiveParticipantBuilder[] getApsForGetOrWadoRS(AuditServiceUtils.EventType eventType, AuditInfo ri, AuditLogger auditLogger) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                ri.getField(AuditInfo.CALLED_AET),
                                getLocalHostName(auditLogger))
                                .altUserID(AuditLogger.processID())
                                .roleIDCode(eventType.source)
                                .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                ri.getField(AuditInfo.DEST_AET),
                                ri.getField(AuditInfo.DEST_NAP_ID))
                                .requester(true)
                                .roleIDCode(eventType.destination)
                                .build();
        return activeParticipantBuilder;
    }

    void spoolPatientRecord(PatientMgtContext ctx) {
        String source = null;
        String dest = null;
        String hl7MessageType = null;
        HL7Segment msh = ctx.getHL7MessageHeader();
        HttpServletRequest request = ctx.getHttpRequest();
        if (request != null) {
            source = KeycloakUtils.getUserName(request);
            dest = request.getRequestURI();
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
        String callingHost = request != null
                ? request.getRemoteAddr()
                : msh != null || ctx.getAssociation() != null
                ? ctx.getRemoteHostName() : null;
        BuildAuditInfo i = new BuildAuditInfo.Builder()
                            .callingHost(callingHost)
                            .callingAET(source)
                            .calledAET(dest)
                            .pIDAndName(ctx.getAttributes(), getArchiveDevice())
                            .outcome(getOD(ctx.getException()))
                            .hl7MessageType(hl7MessageType)
                            .build();
        writeSpoolFile(AuditServiceUtils.EventType.forHL7(ctx), i);
        if (ctx.getPreviousAttributes() != null) {
            BuildAuditInfo prev = new BuildAuditInfo.Builder()
                                    .callingHost(callingHost)
                                    .callingAET(source)
                                    .calledAET(dest)
                                    .pIDAndName(ctx.getPreviousAttributes(), getArchiveDevice())
                                    .outcome(getOD(ctx.getException()))
                                    .hl7MessageType(hl7MessageType)
                                    .build();
            writeSpoolFile(AuditServiceUtils.EventType.PAT_DELETE, prev);
        }
    }

    private void auditPatientRecord(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType et) throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toBuildEventIdentification(et, auditInfo.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));
        ActiveParticipantBuilder[] activeParticipantBuilder = buildPatientRecordActiveParticipants(auditLogger, et, auditInfo);
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, patientPOI(auditInfo));
    }

    private ActiveParticipantBuilder[] buildPatientRecordActiveParticipants(AuditLogger auditLogger, AuditServiceUtils.EventType et, AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        if (isServiceUserTriggered(et.source)) {
            activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                    auditInfo.getField(AuditInfo.CALLED_AET),
                                    getLocalHostName(auditLogger))
                                    .altUserID(AuditLogger.processID())
                                    .roleIDCode(et.destination)
                                    .build();
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                    auditInfo.getField(AuditInfo.CALLING_AET),
                                    auditInfo.getField(AuditInfo.CALLING_HOST))
                                    .requester(true)
                                    .roleIDCode(et.source)
                                    .build();
        } else
            activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                    getAET(),
                                    getLocalHostName(auditLogger))
                                    .altUserID(AuditLogger.processID())
                                    .requester(true)
                                    .roleIDCode(et.destination)
                                    .build();
        return activeParticipantBuilder;
    }

    void spoolProcedureRecord(ProcedureContext ctx) {
        BuildAuditInfo info = ctx.getHttpRequest() != null
                ? buildAuditInfoFORRestful(ctx)
                : ctx.getAssociation() != null ? buildAuditInfoForAssociation(ctx) : buildAuditInfoFORHL7(ctx);
        writeSpoolFile(AuditServiceUtils.EventType.forProcedure(ctx.getEventActionCode()), info);
    }

    private BuildAuditInfo buildAuditInfoForAssociation(ProcedureContext ctx) {
        Association as = ctx.getAssociation();
        return new BuildAuditInfo.Builder()
                .callingHost(ctx.getRemoteHostName())
                .callingAET(as.getCallingAET())
                .calledAET(as.getCalledAET())
                .studyUIDAccNumDate(ctx.getAttributes())
                .pIDAndName(ctx.getPatient().getAttributes(), getArchiveDevice())
                .outcome(getOD(ctx.getException()))
                .build();
    }

    private BuildAuditInfo buildAuditInfoFORRestful(ProcedureContext ctx) {
        HttpServletRequest req  = ctx.getHttpRequest();
        return new BuildAuditInfo.Builder()
                .callingHost(ctx.getRemoteHostName())
                .callingAET(KeycloakUtils.getUserName(req))
                .calledAET(req.getRequestURI())
                .studyUIDAccNumDate(ctx.getAttributes())
                .pIDAndName(ctx.getPatient().getAttributes(), getArchiveDevice())
                .outcome(getOD(ctx.getException()))
                .build();
    }

    private BuildAuditInfo buildAuditInfoFORHL7(ProcedureContext ctx) {
        HL7Segment msh = ctx.getHL7MessageHeader();
        return new BuildAuditInfo.Builder()
                .callingHost(ctx.getRemoteHostName())
                .callingAET(msh.getSendingApplicationWithFacility())
                .calledAET(msh.getReceivingApplicationWithFacility())
                .studyUIDAccNumDate(ctx.getAttributes())
                .pIDAndName(ctx.getPatient().getAttributes(), getArchiveDevice())
                .outcome(getOD(ctx.getException()))
                .hl7MessageType(msh.getMessageType())
                .build();
    }

    void spoolProcedureRecord(StudyMgtContext ctx) {
        String callingAET = KeycloakUtils.getUserName(ctx.getHttpRequest());
        Attributes pAttr = ctx.getStudy() != null ? ctx.getStudy().getPatient().getAttributes() : null;
        BuildAuditInfo info = new BuildAuditInfo.Builder().callingHost(
                                ctx.getHttpRequest().getRemoteHost())
                                .callingAET(callingAET)
                                .calledAET(ctx.getHttpRequest().getRequestURI())
                                .studyUIDAccNumDate(ctx.getAttributes())
                                .pIDAndName(pAttr, getArchiveDevice())
                                .outcome(getOD(ctx.getException()))
                                .build();
        writeSpoolFile(AuditServiceUtils.EventType.forProcedure(ctx.getEventActionCode()), info);
    }

    private void auditProcedureRecord(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType et) throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo prI = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toBuildEventIdentification(et, prI.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));

        ActiveParticipantBuilder[] activeParticipantBuilder = buildProcedureRecordActiveParticipants(auditLogger, prI);
        
        ParticipantObjectDescriptionBuilder desc = new ParticipantObjectDescriptionBuilder.Builder()
                .acc(prI.getField(AuditInfo.ACC_NUM)).build();

        ParticipantObjectIdentificationBuilder poiStudy = new ParticipantObjectIdentificationBuilder.Builder(
                                                        prI.getField(AuditInfo.STUDY_UID),
                                                        AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                                                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                                        AuditMessages.ParticipantObjectTypeCodeRole.Report)
                                                        .desc(desc)
                                                        .detail(getPod(studyDate, prI.getField(AuditInfo.STUDY_DATE)))
                                                        .build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poiStudy, patientPOI(prI));
    }

    private ActiveParticipantBuilder[] buildProcedureRecordActiveParticipants(AuditLogger auditLogger, AuditInfo prI) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                prI.getField(AuditInfo.CALLING_AET),
                                prI.getField(AuditInfo.CALLING_HOST))
                                .requester(true)
                                .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                prI.getField(AuditInfo.CALLED_AET),
                                getLocalHostName(auditLogger))
                                .altUserID(AuditLogger.processID())
                                .build();
        return activeParticipantBuilder;
    }

    void spoolProvideAndRegister(ExportContext ctx) {
        Attributes xdsiManifest = ctx.getXDSiManifest();
        if (xdsiManifest == null)
            return;
        URI dest = ctx.getExporter().getExporterDescriptor().getExportURI();
        String schemeSpecificPart = dest.getSchemeSpecificPart();
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        String destHost = schemeSpecificPart.substring(schemeSpecificPart.indexOf("://")+3, schemeSpecificPart.lastIndexOf(":"));
        BuildAuditInfo info = new BuildAuditInfo.Builder()
                            .callingAET(httpServletRequestInfo.requesterUserID)
                            .callingHost(httpServletRequestInfo.requesterHost)
                            .calledAET(httpServletRequestInfo.requestURI)
                            .destAET(dest.toString())
                            .destNapID(destHost)
                            .outcome(null != ctx.getException() ? ctx.getException().getMessage() : null)
                            .pIDAndName(xdsiManifest, getArchiveDevice())
                            .submissionSetUID(ctx.getSubmissionSetUID()).build();
        writeSpoolFile(AuditServiceUtils.EventType.PROV_REGIS, info);
    }

    private void auditProvideAndRegister(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType et)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toBuildEventIdentification(et, auditInfo.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));

        ActiveParticipantBuilder[] activeParticipantBuilder = buildProvideRegisterActiveParticipants(auditLogger, et, auditInfo);

        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, patientPOI(auditInfo), submissionSetPOI(auditInfo));
    }

    private ActiveParticipantBuilder[] buildProvideRegisterActiveParticipants(
            AuditLogger auditLogger, AuditServiceUtils.EventType et, AuditInfo ai) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                ai.getField(AuditInfo.DEST_AET),
                                ai.getField(AuditInfo.DEST_NAP_ID))
                                .roleIDCode(et.destination)
                                .build();
        if (isServiceUserTriggered(ai.getField(AuditInfo.CALLING_AET))) {
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                    ai.getField(AuditInfo.CALLED_AET),
                                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                                    .roleIDCode(et.source)
                                    .build();
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                                    ai.getField(AuditInfo.CALLING_AET),
                                    ai.getField(AuditInfo.CALLING_HOST))
                                    .requester(true)
                                    .build();
        } else
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                    ai.getField(AuditInfo.CALLED_AET),
                                    getLocalHostName(auditLogger))
                                    .altUserID(AuditLogger.processID())
                                    .requester(true)
                                    .roleIDCode(et.source)
                                    .build();
        return activeParticipantBuilder;
    }

    private boolean isServiceUserTriggered(Object val) {
        return val != null;
    }

    void spoolStgCmt(StgCmtEventInfo stgCmtEventInfo) {
        Attributes eventInfo = stgCmtEventInfo.getExtendedEventInfo();
        Sequence failed = eventInfo.getSequence(Tag.FailedSOPSequence);
        Sequence success = eventInfo.getSequence(Tag.ReferencedSOPSequence);
        String studyUID = eventInfo.getStrings(Tag.StudyInstanceUID) != null
                ? buildStrings(eventInfo.getStrings(Tag.StudyInstanceUID)) : getArchiveDevice().auditUnknownStudyInstanceUID();
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
            BuildAuditInfo i = new BuildAuditInfo.Builder()
                                .callingAET(storageCmtCallingAET(stgCmtEventInfo))
                                .callingHost(storageCmtCallingHost(stgCmtEventInfo))
                                .calledAET(storageCmtCalledAET(stgCmtEventInfo))
                                .pIDAndName(eventInfo, getArchiveDevice())
                                .studyUID(studyUID)
                                .outcome(buildStrings(failureReasons.toArray(new String[failureReasons.size()])))
                                .build();
            objs.add(new AuditInfo(i));
            objs.addAll(aiSet);
            writeSpoolFile(AuditServiceUtils.EventType.STG_COMMIT, objs);
        }
        if (success != null && !success.isEmpty()) {
            BuildAuditInfo[] buildAuditInfos = new BuildAuditInfo[success.size()+1];
            buildAuditInfos[0] = new BuildAuditInfo.Builder()
                                .callingAET(storageCmtCallingAET(stgCmtEventInfo))
                                .callingHost(storageCmtCallingHost(stgCmtEventInfo))
                                .calledAET(storageCmtCalledAET(stgCmtEventInfo))
                                .pIDAndName(eventInfo, getArchiveDevice())
                                .studyUID(studyUID)
                                .build();
            int i = 0;
            for (Attributes item : success) {
                buildAuditInfos[i+1] = new BuildAuditInfo.Builder()
                                    .sopCUID(item.getString(Tag.ReferencedSOPClassUID))
                                    .sopIUID(item.getString(Tag.ReferencedSOPInstanceUID))
                                    .build();
                i++;
            }
            writeSpoolFile(AuditServiceUtils.EventType.STG_COMMIT, buildAuditInfos);
        }
    }

    private String storageCmtCallingHost(StgCmtEventInfo stgCmtEventInfo) {
        try {
            return stgCmtEventInfo.getRemoteAET() != null
                    ? aeCache.findApplicationEntity(stgCmtEventInfo.getRemoteAET()).getConnections().get(0).getHostname()
                    : stgCmtEventInfo.getRequest().getRemoteHost();
        } catch (ConfigurationException e) {
            LOG.error(e.getMessage(), stgCmtEventInfo.getRemoteAET());
        }
        return null;
    }

    private String storageCmtCalledAET(StgCmtEventInfo stgCmtEventInfo) {
        return stgCmtEventInfo.getRequest() != null
                            ? stgCmtEventInfo.getRequest().getRequestURI()
                            : stgCmtEventInfo.getLocalAET();
    }

    private String storageCmtCallingAET(StgCmtEventInfo stgCmtEventInfo) {
        return stgCmtEventInfo.getRequest() != null
                                ? KeycloakUtils.getUserName(stgCmtEventInfo.getRequest())
                                : stgCmtEventInfo.getRemoteAET();
    }

    private void auditStorageCommit(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType et) throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toBuildEventIdentification(et, auditInfo.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(auditInfo.getField(AuditInfo.CALLED_AET),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                .roleIDCode(et.destination).build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(auditInfo.getField(AuditInfo.CALLING_AET),
                auditInfo.getField(AuditInfo.CALLING_HOST)).requester(true).roleIDCode(et.source).build();
       
        String[] studyUIDs = StringUtils.split(auditInfo.getField(AuditInfo.STUDY_UID), ';');
        
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (String line : reader.getInstanceLines()) {
            AuditInfo ii = new AuditInfo(line);
            buildSOPClassMap(sopClassMap, ii.getField(AuditInfo.SOP_CUID), ii.getField(AuditInfo.SOP_IUID));
        }
        HashSet<SOPClass> sopC = new HashSet<>();
        if (studyUIDs.length > 1 || auditInfo.getField(AuditInfo.OUTCOME) != null)
            for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet())
                sopC.add(getSOPC(entry.getValue(), entry.getKey(), entry.getValue().size()));
        else
            for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet())
                sopC.add(getSOPC(null, entry.getKey(), entry.getValue().size()));

        ParticipantObjectDescriptionBuilder poDesc = new ParticipantObjectDescriptionBuilder.Builder()
                .sopC(sopC.toArray(new SOPClass[sopC.size()]))
                .pocsStudyUIDs(studyUIDs).build();
        
        ParticipantObjectIdentificationBuilder poiStudy = new ParticipantObjectIdentificationBuilder.Builder(studyUIDs[0],
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(poDesc).lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.Verification).build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poiStudy, patientPOI(auditInfo));
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
                : req != null ? KeycloakUtils.getUserName(req) : callingHost;
        if (callingAET == null && callingHost == null)
            callingAET = ss.toString();
        String outcome = null != ctx.getException() ? null != ctx.getRejectionNote()
                ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() + " - " + ctx.getException().getMessage()
                : getOD(ctx.getException()) : null;
        String warning = ctx.getException() == null && null != ctx.getRejectionNote()
                ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() : null;
        return new BuildAuditInfo.Builder().callingHost(callingHost)
                .callingAET(callingAET)
                .calledAET(req != null ? req.getRequestURI() : ss.getCalledAET())
                .studyUIDAccNumDate(attr)
                .pIDAndName(attr, getArchiveDevice())
                .outcome(outcome)
                .warning(warning)
                .build();
    }

    private String getFileName(AuditServiceUtils.EventType et, String callingAET, String calledAET, String studyIUID) {
        return String.valueOf(et) + '-' + callingAET + '-' + calledAET + '-' + studyIUID;
    }

    private String getOD(Exception e) {
        return e != null ? e.getMessage() : null;
    }

    private ParticipantObjectDetail getPod(String type, String value) {
        return value != null ? AuditMessages.createParticipantObjectDetail(type, value.getBytes()) : null;
    }

    private SOPClass[] sopClasses(List<String> instanceLines) {
        SOPClass[] sopClasses = new SOPClass[instanceLines.size()];
        for (int i = 0; i < instanceLines.size(); i++) {
            AuditInfo ii = new AuditInfo(instanceLines.get(i));
            sopClasses[i] = AuditMessages.createSOPClass(
                    null, ii.getField(AuditInfo.SOP_CUID), Integer.parseInt(ii.getField(AuditInfo.SOP_IUID)));
        }
        return sopClasses;
    }

    private SOPClass getSOPC(HashSet<String> instances, String uid, Integer numI) {
        return AuditMessages.createSOPClass(instances, uid, numI);
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

    private String getAET() {
        String[] aets = device.getApplicationAETitles().toArray(new String[device.getApplicationAETitles().size()]);
        StringBuilder b = new StringBuilder();
        b.append(aets[0]);
        for (int i = 1; i < aets.length; i++)
            b.append(';').append(aets[i]);
        return b.toString();
    }

    private String getLocalHostName(AuditLogger log) {
        return log.getConnections().get(0).getHostname();
    }

    private void writeSpoolFile(AuditServiceUtils.EventType eventType, LinkedHashSet<Object> obj) {
        if (obj.isEmpty()) {
            LOG.warn("Attempt to write empty file : ", eventType);
            return;
        }
        ArchiveDeviceExtension arcDev = getArchiveDevice();
        boolean auditAggregate = arcDev.isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = toDirPath(auditLogger);
                try {
                    Files.createDirectories(dir);
                    Path file = Files.createTempFile(dir, String.valueOf(eventType), null);
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

    private Path toDirPath(AuditLogger auditLogger) {
        return Paths.get(
                StringUtils.replaceSystemProperties(getArchiveDevice().getAuditSpoolDirectory()),
                auditLogger.getCommonName().replaceAll(" ", "_"));
    }

    private void writeSpoolFile(AuditServiceUtils.EventType eventType, BuildAuditInfo... buildAuditInfos) {
        if (buildAuditInfos == null) {
            LOG.warn("Attempt to write empty file : ", eventType);
            return;
        }
        boolean auditAggregate = getArchiveDevice().isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = toDirPath(auditLogger);
                try {
                    Files.createDirectories(dir);
                    Path file = Files.createTempFile(dir, String.valueOf(eventType), null);
                    try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            StandardOpenOption.APPEND))) {
                        for (BuildAuditInfo buildAuditInfo : buildAuditInfos)
                            writer.writeLine(new AuditInfo(buildAuditInfo));
                    }
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File - {} ", auditLogger.getCommonName(), e);
                }
            }
        }
    }

    private void writeSpoolFileStoreOrWadoRetrieve(String fileName, BuildAuditInfo patStudyInfo, BuildAuditInfo instanceInfo) {
        if (patStudyInfo == null && instanceInfo == null) {
            LOG.warn("Attempt to write empty file : " + fileName);
            return;
        }
        boolean auditAggregate = getArchiveDevice().isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = toDirPath(auditLogger);
                Path file = dir.resolve(fileName);
                boolean append = Files.exists(file);
                try {
                    if (!append)
                        Files.createDirectories(dir);
                    try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW))) {
                        if (!append) {
                            writer.writeLine(new AuditInfo(patStudyInfo));
                        }
                        writer.writeLine(new AuditInfo(instanceInfo));
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

    private void emitAuditMessage(
            AuditLogger logger, EventIdentificationBuilder eventIdentificationBuilder, ActiveParticipantBuilder[] activeParticipantBuilder,
            ParticipantObjectIdentificationBuilder... participantObjectIdentificationBuilder) {
        AuditMessage msg = AuditMessages.createMessage(eventIdentificationBuilder, activeParticipantBuilder, participantObjectIdentificationBuilder);
        msg.getAuditSourceIdentification().add(logger.createAuditSourceIdentification());
        try {
            logger.write(logger.timeStamp(), msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", logger.getCommonName(), e);
        }
    }

    private String getEOI(String outcomeDesc) {
        return outcomeDesc != null ? AuditMessages.EventOutcomeIndicator.MinorFailure : AuditMessages.EventOutcomeIndicator.Success;
    }

    private EventIdentificationBuilder toCustomBuildEventIdentification(AuditServiceUtils.EventType et, String failureDesc, String warningDesc, Calendar t) {
        return failureDesc != null
                ? toBuildEventIdentification(et, failureDesc, t)
                : new EventIdentificationBuilder.Builder(
                    et.eventID, et.eventActionCode, t, AuditMessages.EventOutcomeIndicator.Success)
                    .outcomeDesc(warningDesc).build();
    }

    private EventIdentificationBuilder toBuildEventIdentification(AuditServiceUtils.EventType et, String desc, Calendar t) {
        return new EventIdentificationBuilder.Builder(
                et.eventID, et.eventActionCode, t, getEOI(desc)).outcomeDesc(desc).eventTypeCode(et.eventTypeCode).build();
    }
    
    private ParticipantObjectIdentificationBuilder patientPOI(AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.P_ID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(auditInfo.getField(AuditInfo.P_NAME))
                .detail(getPod("HL7MessageType", auditInfo.getField(AuditInfo.HL7_MESSAGE_TYPE)))
                .build();
    }

    private ParticipantObjectIdentificationBuilder submissionSetPOI(AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.SUBMISSION_SET_UID),
                AuditMessages.ParticipantObjectIDTypeCode.IHE_XDS_METADATA,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Job)
                .build();
    }
}
