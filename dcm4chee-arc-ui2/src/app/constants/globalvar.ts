export class Globalvar {
    public static get MODALITIES(): any {
        return {
            'common': {
                'CR': 'Computed Radiography',
                'CT': 'Computed Tomography',
                'DX': 'Digital Radiography',
                'KO': 'Key Object Selection',
                'MR': 'Magnetic Resonance',
                'MG': 'Mammography',
                'NM': 'Nuclear Medicine',
                'OT': 'Other',
                'PT': 'Positron emission tomography (PET)',
                'PR': 'Presentation State',
                'US': 'Ultrasound',
                'XA': 'X-Ray Angiography'
            },
            'more': {
                'AR': 'Autorefraction',
                'AU': 'Audio',
                'BDUS': 'Bone Densitometry (ultrasound)',
                'BI': 'Biomagnetic imaging',
                'BMD': 'Bone Densitometry (X-Ray)',
                'DOC': 'Document',
                'DG': 'Diaphanography',
                'ECG': 'Electrocardiography',
                'EPS': 'Cardiac Electrophysiology',
                'ES': 'Endoscopy',
                'FID': 'Fiducials',
                'GM': 'General Microscopy',
                'HC': 'Hard Copy',
                'HD': 'Hemodynamic Waveform',
                'IO': 'Intra-Oral Radiography',
                'IOL': 'Intraocular Lens Data',
                'IVOCT': 'Intravascular Optical Coherence Tomography',
                'IVUS': 'Intravascular Ultrasound',
                'KER': 'Keratometry',
                'LEN': 'Lensometry',
                'LS': 'Laser surface scan',
                'OAM': 'Ophthalmic Axial Measurements',
                'OCT': 'Optical Coherence Tomography (non-Ophthalmic)',
                'OP': 'Ophthalmic Photography',
                'OPM': 'Ophthalmic Mapping',
                'OPT': 'Ophthalmic Tomography',
                'OPV': 'Ophthalmic Visual Field',
                'OSS': 'Optical Surface Scan',
                'PLAN': 'Plan',
                'PX': 'Panoramic X-Ray',
                'REG': 'Registration',
                'RESP': 'Respiratory Waveform',
                'RF': 'Radio Fluoroscopy',
                'RG': 'Radiographic imaging (conventional film/screen)',
                'RTDOSE': 'Radiotherapy Dose',
                'RTIMAGE': 'Radiotherapy Image',
                'RTPLAN': 'Radiotherapy Plan',
                'RTRECORD': 'RT Treatment Record',
                'RTSTRUCT': 'Radiotherapy Structure Set',
                'RWV': 'Real World Value Map',
                'SEG': 'Segmentation',
                'SM': 'Slide Microscopy',
                'SMR': 'Stereometric Relationship',
                'SR': 'SR Document',
                'SRF': 'Subjective Refraction',
                'STAIN': 'Automated Slide Stainer',
                'TG': 'Thermography',
                'VA': 'Visual Acuity',
                'XC': 'External-camera Photography'
            }
        };
    }
    public static get OPTIONS(): any{
        return  {genders:
            [
                {
                    obj: {
                        'vr': 'CS',
                        'Value': ['F']
                    },
                    'title': 'Female'
                },
                {
                    obj: {
                        'vr': 'CS',
                        'Value': ['M']
                    },
                    'title': 'Male'
                },
                {
                    obj: {
                        'vr': 'CS',
                        'Value': ['O']
                    },
                    'title': 'Other'
                }
            ]
        };
    }
    public static get ORDERBY_EXTERNAL(): Array<any>{
        return [
            {
                value: '',
                label: '<label>Patient </label>',
                mode: 'patient',
                title:'Query Patients to external archive'
            },{
                value: '',
                label: '<label>Study </label>',
                mode: 'study',
                title:'Query Studies to external archive'
            }
        ]
    }
    public static get ORDERBY(): Array<any>{
        return [
            {
                value: 'PatientName',
                label: '<label>Patient</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span>',
                mode: 'patient',
                title:'Query Patients'
            },
            {
                value: '-PatientName',
                label: '<label>Patient</label><span class=\"orderbynamedesc\"></span>',
                mode: 'patient',
                title:'Query Patients'
            },
            {

                value: '-StudyDate,-StudyTime',
                label: '<label>Study</label><span class=\"orderbydateasc\"></span>',
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: 'StudyDate,StudyTime',
                label: '<label>Study</label><span class=\"orderbydatedesc\"></span>',
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: 'PatientName,-StudyDate,-StudyTime',
                label: '<label>Study</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>',
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: '-PatientName,-StudyDate,-StudyTime',
                label: '<label>Study</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>',
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: 'PatientName,StudyDate,StudyTime',
                label: '<label>Study</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>',
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: '-PatientName,StudyDate,StudyTime',
                label: '<label>Study</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>',
                mode: 'study',
                title:'Query Studies'
            },
            {
                value: '-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: '<label>MWL</label></span><span class=\"orderbydateasc\"></span>',
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: 'ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: '<label>MWL</label><span class=\"orderbydatedesc\"></span>',
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: 'PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: '<label>MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydateasc\"></span>',
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: '-PatientName,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,-ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: '<label>MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydateasc\"></span>',
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: 'PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: '<label>MWL</label><span class=\"glyphicon glyphicon-sort-by-alphabet\"></span><span class=\"orderbydatedesc\"></span>',
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: '-PatientName,ScheduledProcedureStepSequence.ScheduledProcedureStepStartDate,ScheduledProcedureStepSequence.ScheduledProcedureStepStartTime',
                label: '<label>MWL</label><span class=\"orderbynamedesc\"></span><span class=\"orderbydatedesc\"></span>',
                mode: 'mwl',
                title:'Query MWL'
            },
            {
                value: '',
                label: '<label>Diff </label><i class="material-icons">compare_arrows</i>',
                mode: 'diff',
                title:'Make diff between two archives'
            }
        ];

    }
    /*
    * Defines action for replacing placehoders/title or disabling elements when you edit or create patient,mwl or study
    * Used in helpers/placeholderchanger.directive.ts
    * */
    public static get IODPLACEHOLDERS(): any{
        return {
            '00100020': {
                'create': {
                    placeholder: 'To generate it automatically leave it blank',
                    action: 'replace'
                }
            },
            '0020000D': {
                'create': {
                    placeholder: 'To generate it automatically leave it blank',
                    action: 'replace'
                },
                'edit': {
                    action: 'disable'
                }
            },
            '00400009': {
                'edit': {
                    action: 'disable'
                }
            }
        };
    };

    public static get HISTOGRAMCOLORS(): any{
        return [
            {
                backgroundColor: 'rgba(62, 83, 98, 0.84)'
            },
            {
                backgroundColor: 'rgba(0, 32, 57, 0.84)'
            },
            {
                backgroundColor: 'rgba(97, 142, 181, 0.84)'
            },
            {
                backgroundColor: 'rgba(38, 45, 51, 0.84)'
            },
            {
                backgroundColor: 'rgba(0, 123, 90, 0.84)'
            },
            {
                backgroundColor: 'rgba(56, 38, 109, 0.84)'
            },
            {
                backgroundColor: 'rgba(109, 41, 41, 0.84)'
            },
            {
                backgroundColor: 'rgba(20, 55, 16, 0.84)'
            },
            {
                backgroundColor: 'rgba(54, 111, 121, 0.84)'
            },
            {
                backgroundColor: 'rgba(249,168,37 ,0.84)'
            },
            {
                backgroundColor: 'rgba(3,169,244 ,0.84)'
            },
            {
                backgroundColor: 'rgba(40,53,147 ,0.84)'
            },
            {
                backgroundColor: 'rgba(142,36,170 ,0.84)'
            },
            {
                backgroundColor: 'rgba(183,28,28 ,0.84)'
            },
            {
                backgroundColor: 'rgba(240,98,146 ,0.84)'
            },
            {
                backgroundColor: 'rgba(121,85,72 ,0.84)'
            },
            {
                backgroundColor: 'rgba(33,33,33 ,0.84)'
            },
            {
                backgroundColor: 'rgba(144,164,174 ,0.84)'
            },
            {
                backgroundColor: 'rgba(38,166,154 ,0.84)'
            },
            {
                backgroundColor: 'rgba(159,168,218 ,0.84)'
            },
            {
                backgroundColor: 'rgba(213,0,0 ,0.84)'
            },
            {
                backgroundColor: 'rgba(24,255,255 ,0.84)'
            },
            {
                backgroundColor: 'rgba(0,188,212,0.84)'
            },
            {
                backgroundColor: 'rgba(63,81,181,0.84)'
            },
            {
                backgroundColor: 'rgba(213,0,249 ,0.84)'
            },
            {
                backgroundColor: 'rgba(156,204,101 ,0.84)'
            },
            {
                backgroundColor: 'rgba(255,111,0 ,0.84)'
            },
            {
                backgroundColor: 'rgba(109,135,100 ,0.84)'
            },
            {
                backgroundColor: 'rgba(255,82,82 ,0.84)'
            },
            {
                backgroundColor: 'rgba(229,115,140 ,0.84)'
            },
            {
                backgroundColor: 'rgba(21,45,115 ,0.84)'
            }
        ]
    }
    public static get ELASTICSEARCHDOMAIN(): any{
        return "http://localhost:9200";
    };

    public static get STUDIESSTOREDCOUNTS_PARAMETERS(): any{
        return {
            "size": 0,
            "aggs": {
                "1": {
                    "cardinality": {
                        "field": "Study.ParticipantObjectID"
                    }
                }
            },
            "highlight": {
                "fields": {
                    "*": {}
                },
                "require_field_match": false,
                "fragment_size": 2147483647
            },
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "EventID.csd-code:110104 AND Event.EventActionCode:C",
                                "analyze_wildcard": true
                            }
                        },
                        {
                            "query_string": {
                                "analyze_wildcard": true,
                                "query": "*"
                            }
                        }
                    ],
                    "must_not": []
                }
            },
            "_source": {
                "excludes": []
            }
        };
    };

    public static get QUERIESUSERID_PARAMETERS(): any{
        return (aets)=>{
            return {
                "size": 0,
                "aggs": {
                    "2": {
                        "date_histogram": {
                            "field": "Event.EventDateTime",
                            "interval": "3h",
                            "time_zone": "Europe/Berlin",
                            "min_doc_count": 1
                        },
                        "aggs": {
                            "3": {
                                "terms": {
                                    "field": "Source.UserID",
                                    "size": 15,
                                    "order": {
                                        "_count": "desc"
                                    }
                                }
                            }
                        }
                    }
                },
                "query": {
                    "bool": {
                        "must": [
                            {
                                "query_string": {
                                    "query": `EventID.csd-code:110112 AND (${aets})`, //TODO use dynamic names of the aets service
                                    "analyze_wildcard": true
                                }
                            }
                        ],
                        "must_not": []
                    }
                },
                "_source": {
                    "excludes": []
                }
            }
        };
    }
    public static get WILDFLYERRORCOUNTS_PARAMETERS(): any{
        return {
            "size": 0,
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "Severity:ERROR",
                                "analyze_wildcard": true
                            }
                        },
                        {
                            "query_string": {
                                "analyze_wildcard": true,
                                "query": "*"
                            }
                        }
                    ]
                }
            }
        };
    }
    public static get ERRORSCOUNTS_PARAMETERS(): any{
        return {
            "aggs": {},
            "highlight": {
                "fields": {
                    "*": {}
                },
                "require_field_match": false,
                "fragment_size": 2147483647
            },
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "NOT Event.EventOutcomeIndicator:0",
                                "analyze_wildcard": true
                            }
                        },
                        {
                            "query_string": {
                                "analyze_wildcard": true,
                                "query": "*"
                            }
                        }
                    ],
                    "must_not": []
                }
            },
            "_source": {
                "excludes": []
            }
        }
    }
    public static get QUERIESCOUNTS_PARAMETERS(): any{
        return {
            "size":0,
            "query": {
                "bool": {
                    "must":[
                        {
                            "query_string": {
                                "query": "EventID.csd-code:110112",
                                "analyze_wildcard": true
                            }
                        }
                    ]
                    ,
                    "must_not": [{
                        "wildcard":{"Destination.UserID":"*/*"} //Get all entries but thous who have slashes in there in Destination.UserID
                    }]
                }
            },
            "aggs" :{
                "2":{
                    "date_histogram": {
                        "field": "Event.EventDateTime",
                        "interval": "1D",
                        "time_zone": "Europe/Berlin",
                        "min_doc_count": 1
                    },
                    "aggs":{
                        "3" : {
                            "terms" : {
                                "field" : "Destination.UserID"
                            }
                        }
                    }
                }}
        }
    }
    public static get STUDIESSTOREDRECIVINGAET_PARAMETERS(): any{
        return {
            "size": 0,
            "aggs": {
                "2": {
                    "date_histogram": {
                        "field": "Event.EventDateTime",
                        "interval": "30m",
                        "time_zone": "Europe/Berlin",
                        "min_doc_count": 1
                    },
                    "aggs": {
                        "3": {
                            "terms": {
                                "field": "Destination.UserID",
                                "size": 5,
                                "order": {
                                    "1": "desc"
                                }
                            },
                            "aggs": {
                                "1": {
                                    "cardinality": {
                                        "field": "Study.ParticipantObjectID"
                                    }
                                }
                            }
                        }
                    }
                }
            },
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "EventID.csd-code:110104 AND Event.EventActionCode:C",
                                "analyze_wildcard": true
                            }
                        }
                    ]
                    ,
                    "must_not": [{
                        "wildcard":{"Destination.UserID":"*/*"}
                    }]
                }
            }
        }

    }
    public static get STUDIESSTOREDUSERID_PARAMETERS(): any{
        return {
            "size": 0,
            "aggs": {
                "2": {
                    "date_histogram": {
                        "field": "Event.EventDateTime",
                        "interval": "12h",
                        "time_zone": "Europe/Berlin",
                        "min_doc_count": 1
                    },
                    "aggs": {
                        "3": {
                            "terms": {
                                "field": "Source.UserID",
                                "size": 15,
                                "order": {
                                    "1": "desc"
                                }
                            },
                            "aggs": {
                                "1": {
                                    "cardinality": {
                                        "field": "Study.ParticipantObjectID"
                                    }
                                }
                            }
                        }
                    }
                }
            },
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "EventID.csd-code:110104 AND Event.EventActionCode:C",
                                "analyze_wildcard": true
                            }
                        }
                    ]
                }
            }
        }
    }

    public static get CPU_PARAMETERS():any{
        return {
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "*",
                                "analyze_wildcard": true
                            }
                        }
                    ]
                }
            },
            "size": 0,
            "_source": {
                "excludes": []
            },
            "aggs": {
                "2": {
                    "date_histogram": {
                        "field": "@timestamp",
                        "interval": "30s",
                        "time_zone": "Europe/Berlin",
                        "min_doc_count": 1
                    },
                    "aggs": {
                        "3": {
                            "terms": {
                                "field": "containerName",
                                "size": 30,
                                "order": {
                                    "1": "desc"
                                }
                            },
                            "aggs": {
                                "1": {
                                    "max": {
                                        "field": "cpu.totalUsage"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    public static get MEMORY_RSS_PARAMETERS():any{
        return {
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "*",
                                "analyze_wildcard": true
                            }
                        }
                    ]
                }
            },
            "size": 0,
            "_source": {
                "excludes": []
            },
            "aggs": {
                "2": {
                    "date_histogram": {
                        "field": "@timestamp",
                        "interval": "30s",
                        "time_zone": "Europe/Berlin",
                        "min_doc_count": 1
                    },
                    "aggs": {
                        "3": {
                            "terms": {
                                "field": "containerName",
                                "size": 20,
                                "order": {
                                    "1": "desc"
                                }
                            },
                            "aggs": {
                                "1": {
                                    "max": {
                                        "field": "memory.totalRss"
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
    }
    public static get MEMORY_USAGE_PARAMETERS():any{
        return {
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "*",
                                "analyze_wildcard": true
                            }
                        }
                    ]
                }
            },
            "size": 0,
            "_source": {
                "excludes": []
            },
            "aggs": {
                "2": {
                    "date_histogram": {
                        "field": "@timestamp",
                        "interval": "30s",
                        "time_zone": "Europe/Berlin",
                        "min_doc_count": 1
                    },
                    "aggs": {
                        "3": {
                            "terms": {
                                "field": "containerName",
                                "size": 20,
                                "order": {
                                    "1": "desc"
                                }
                            },
                            "aggs": {
                                "1": {
                                    "max": {
                                        "field": "memory.usage"
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
    }
    public static get WRITE_PER_SECOND_PARAMETERS():any{
        return {
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "*",
                                "analyze_wildcard": true
                            }
                        }
                    ]
                }
            },
            "size": 0,
            "_source": {
                "excludes": []
            },
            "aggs": {
                "2": {
                    "date_histogram": {
                        "field": "@timestamp",
                        "interval": "30s",
                        "time_zone": "Europe/Berlin",
                        "min_doc_count": 1
                    },
                    "aggs": {
                        "3": {
                            "terms": {
                                "field": "containerName",
                                "size": 20,
                                "order": {
                                    "1": "desc"
                                }
                            },
                            "aggs": {
                                "1": {
                                    "max": {
                                        "field": "blkio.write_ps"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    public static get READ_PER_SECOND_PARAMETERS():any{
        return {
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "*",
                                "analyze_wildcard": true
                            }
                        }
                    ]
                }
            },
            "size": 0,
            "_source": {
                "excludes": []
            },
            "aggs": {
                "2": {
                    "date_histogram": {
                        "field": "@timestamp",
                        "interval": "30s",
                        "time_zone": "Europe/Berlin",
                        "min_doc_count": 1
                    },
                    "aggs": {
                        "3": {
                            "terms": {
                                "field": "containerName",
                                "size": 20,
                                "order": {
                                    "1": "desc"
                                }
                            },
                            "aggs": {
                                "1": {
                                    "max": {
                                        "field": "blkio.read_ps"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    public static get NETWORK_TRANSMITTED_PACKETS_PARAMETERS():any{
        return {
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "*",
                                "analyze_wildcard": true
                            }
                        }
                    ]
                }
            },
            "size": 0,
            "_source": {
                "excludes": []
            },
            "aggs": {
                "2": {
                    "date_histogram": {
                        "field": "@timestamp",
                        "interval": "30s",
                        "time_zone": "Europe/Berlin",
                        "min_doc_count": 1
                    },
                    "aggs": {
                        "3": {
                            "terms": {
                                "field": "containerName",
                                "size": 30,
                                "order": {
                                    "1": "desc"
                                }
                            },
                            "aggs": {
                                "1": {
                                    "max": {
                                        "field": "net.txPackets_ps"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static get STUDIESSTOREDSOPCLASS_PARAMETERS(): any{
        return {
            "size": 0,
            "aggs": {
                "2": {
                    "date_histogram": {
                        "field": "Event.EventDateTime",
                        "interval": "3h",
                        "time_zone": "Europe/Berlin",
                        "min_doc_count": 1
                    },
                    "aggs": {
                        "3": {
                            "terms": {
                                "field": "Study.ParticipantObjectDescription.SOPClass.UID",
                                "size": 5,
                                "order": {
                                    "1": "desc"
                                }
                            },
                            "aggs": {
                                "1": {
                                    "cardinality": {
                                        "field": "Study.ParticipantObjectID"
                                    }
                                }
                            }
                        }
                    }
                }
            },
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "EventID.csd-code:110104 AND Event.EventActionCode:C",
                                "analyze_wildcard": true
                            }
                        }
                    ]
                }
            }
        };
    }
    public static get RETRIEVESUSERID_PARAMETERS(): any{
        return {
            "size": 0,
            "aggs": {
                "2": {
                    "date_histogram": {
                        "field": "Event.EventDateTime",
                        "interval": "1w",
                        "time_zone": "Europe/Berlin",
                        "min_doc_count": 1
                    },
                    "aggs": {
                        "3": {
                            "terms": {
                                "field": "Destination.UserID",
                                "size": 5,
                                "order": {
                                    "1": "desc"
                                }
                            },
                            "aggs": {
                                "1": {
                                    "cardinality": {
                                        "field": "Study.ParticipantObjectID"
                                    }
                                }
                            }
                        }
                    }
                }
            },
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "EventID.csd-code:110104 AND Event.EventActionCode:R",
                                "analyze_wildcard": true
                            }
                        },
                        {
                            "query_string": {
                                "analyze_wildcard": true,
                                "query": "*"
                            }
                        }
                    ],
                    "must_not": []
                }
            },
            "_source": {
                "excludes": []
            }
        };
    }
    public static get RETRIEVCOUNTS_PARAMETERS(): any{
        return {
            "size": 0,
            "aggs": {},
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "EventID.csd-code:110104 AND Event.EventActionCode:R",
                                "analyze_wildcard": true
                            }
                        },
                        {
                            "query_string": {
                                "analyze_wildcard": true,
                                "query": "*"
                            }
                        }
                    ],
                    "must_not": []
                }
            },
            "_source": {
                "excludes": []
            }
        };
    }
    public static get AUDITEVENTS_PARAMETERS(): any{
        return {
            "query": {
                "bool": {
                    "must": [
                        {
                            "query_string": {
                                "query": "*",
                                "analyze_wildcard": true
                            }
                        },
                        {
                            "query_string": {
                                "analyze_wildcard": true,
                                "query": "*"
                            }
                        }
                    ],
                    "must_not": []
                }
            },
            "size": 3000,
            "sort": [
                {
                    "Event.EventDateTime": {
                        "order": "desc",
                        "unmapped_type": "boolean"
                    }
                }
            ],
            "_source": {
                "excludes": []
            },
            "stored_fields": [
                "*"
            ],
            "script_fields": {},
            "docvalue_fields": [
                "audit.EventIdentification.EventDateTime",
                "Event.EventDateTime",
                "StudyDate",
                "@timestamp",
                "syslog_timestamp"
            ]
        };
    }

    public static get EXPORT_STUDY_EXTERNAL_URL(): any{
        ///aets/{aet}/dimse/{externalAET}/studies/{StudyInstanceUID}/export/dicom:{destinationAET}
        return (aet,externalAET,StudyInstanceUID,destinationAET) => `../aets/${aet}/dimse/${externalAET}/studies/${StudyInstanceUID}/export/dicom:${destinationAET}`;
    }

    public static get HL7_SPECIFIC_CHAR(): any{
        return [
            {
                groupName:"Single-Byte Character Sets",
                groupValues:[
                    {
                        title: "ASCII",
                        value: "ASCII"
                    },
                    {
                        title:"GB 18030-2000",
                        value:"GB 18030-2000"
                    },
                    {
                        title:"Latin alphabet No. 1",
                        value:"8859/1"
                    },

                    {
                        title:"Latin alphabet No. 2",
                        value:"8859/2"
                    },
                    {
                        title:"Thai",
                        value:"CNS 11643-1992"
                    },
                    {
                        title:"Latin alphabet No. 3",
                        value:"8859/3"
                    },
                    {
                        title:"Latin alphabet No. 4",
                        value:"8859/4"
                    },
                    {
                        title:"Japanese",
                        value:"ISO IR14"
                    },
                    {
                        title:"Cyrillic",
                        value:"8859/5"
                    },
                    {
                        title:"Arabic",
                        value:"8859/6"
                    },
                    {
                        title:"Greek",
                        value:"8859/7"
                    },

                    {
                        title:"Hebrew",
                        value:"8859/8"
                    },
                    {
                        title:"Latin alphabet No. 5",
                        value:"8859/9"
                    }
                ]
            },{
                groupName:"Multi-Byte Character Sets",
                groupValues:[
                    {
                        title:"Japanese (Kanji)",
                        value:"ISO IR87"
                    },{
                        title:"Japanese (Supplementary Kanji set)",
                        value:"ISO IR159"
                    },{
                        title:"Korean",
                        value:"KS X 1001"
                    },{
                        title:"Unicode",
                        value:"UNICODE"
                    },
                    {
                        title:"Unicode in UTF-8",
                        value:"UNICODE UTF-8"
                    }
                ]
            }
        ]
    }
    public static get DICOM_SPECIFIC_CHAR(): any{
        return [
            {
                groupName:"Single-Byte Character Sets",
                groupValues:[
                    {
                        title:"Latin alphabet No. 1",
                        value:"ISO_IR 100"
                    },
                    {
                        title:"Latin alphabet No. 2",
                        value:"ISO_IR 101"
                    },
                    {
                        title:"Latin alphabet No. 3",
                        value:"ISO_IR 109"
                    },
                    {
                        title:"Latin alphabet No. 4",
                        value:"ISO_IR 110"
                    },
                    {
                        title:"Cyrillic",
                        value:"ISO_IR 144"
                    },
                    {
                        title:"Arabic",
                        value:"ISO_IR 127"
                    },
                    {
                        title:"Greek",
                        value:"ISO_IR 126"
                    },
                    {
                        title:"Hebrew",
                        value:"ISO_IR 138"
                    },
                    {
                        title:"Latin alphabet No. 5",
                        value:"ISO_IR 148"
                    },
                    {
                        title:"Japanese",
                        value:"ISO_IR 13"
                    },
                    {
                        title:"Thai",
                        value:"ISO_IR 166"
                    }
                ]
            },{
                groupName:"Multi-Byte Character Sets Without Code Extensions",
                groupValues:[
                    {
                        title:"Unicode in UTF-8",
                        value:"ISO_IR 192"
                    },{
                        title:"GB18030",
                        value:"GB18030"
                    },{
                        title:"GBK",
                        value:"GBK"
                    }
                ]
            },
            {
                groupName:"Single-Byte Character Sets with Code Extensions",
                groupValues:[
                    {
                        title:"Default repertoire",
                        value:"ISO 2022 IR 6"
                    },{
                        title:"Latin alphabet No. 1",
                        value:"ISO 2022 IR 100"
                    },
                    {
                        title:"Latin alphabet No. 2",
                        value:"ISO 2022 IR 101"
                    },
                    {
                        title:"Latin alphabet No. 3",
                        value:"ISO 2022 IR 109"
                    },
                    {
                        title:"Latin alphabet No. 4",
                        value:"ISO 2022 IR 110"
                    },
                    {
                        title:"Cyrillic",
                        value:"ISO 2022 IR 144"
                    },
                    {
                        title:"Arabic",
                        value:"ISO 2022 IR 127"
                    },
                    {
                        title:"Greek",
                        value:"ISO 2022 IR 126"
                    },
                    {
                        title:"Hebrew",
                        value:"ISO 2022 IR 138"
                    },
                    {
                        title:"Latin alphabet No. 5",
                        value:"ISO 2022 IR 148"
                    },
                    {
                        title:"Japanese",
                        value:"ISO 2022 IR 13"
                    },
                    {
                        title:"Thai",
                        value:"ISO 2022 IR 166"
                    }
                ]
            },{
                groupName:"Multi-Byte Character Sets",
                groupValues:[
                    {
                        title:"Japanese (Kanji)",
                        value:"ISO 2022 IR 87"
                    },{
                        title:"Japanese (Supplementary Kanji set)",
                        value:"ISO 2022 IR 159"
                    },{
                        title:"Korean",
                        value:"ISO 2022 IR 149"
                    },{
                        title:"Simplified Chinese",
                        value:"ISO 2022 IR 58"
                    }
                ]
            }
        ]

    }
    public static get DYNAMIC_FORMATER(): any{
        return {
            dcmAETitle:{
                key:'dicomAETitle',
                labelKey:'{dicomAETitle}',
                msg:'Create first an AE Title!'
            },
            dcmArchiveAETitle:{
                key:'dicomAETitle',
                labelKey:'{dicomAETitle}',
                msg:'Create first an AE Title!',
                pathInDevice:'dicomNetworkAE'
            },
            dcmQueueName:{
                key:'dcmQueueName',
                labelKey:'{dcmQueueName}',
                msg:'Configure first an Queue',
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmQueue'
            },
            dcmExporterID:{
                key:'dcmExporterID',
                labelKey:'{dcmExporterID}',
                msg:'Create first an Exporter!',
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmExporter'
            },
            dcmStorageID:{
                key:'dcmStorageID',
                labelKey:'{dcmStorageID}',
                msg:'Create first an Storage!',
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmStorage'
            },
            dcmQueryRetrieveViewID:{
                key:'dcmQueryRetrieveViewID',
                labelKey:'{dcmQueryRetrieveViewID}',
                msg:'Create first an Query Retrieve View!',
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmQueryRetrieveView'
            },
            dcmRejectionNoteCode:{
                key:'dcmRejectionNoteCode',
                labelKey:'{dcmRejectionNoteLabel}',
                msg:'Create first an Rejection Note!',
                pathInDevice:'dcmDevice.dcmArchiveDevice.dcmRejectionNote'
            },
            dicomDeviceName:{
                key:'dicomDeviceName',
                labelKey:'{dicomDeviceName}',
                msg:'Create first any device first!'
            },
            hl7ApplicationName:{
                key:'hl7ApplicationName',
                labelKey:'{hl7ApplicationName}',
                msg:'Create first an hl7 Application!'
            }
        };
    }
    public static get HL7_LIST_LINK(): string{
        return "../hl7apps";
    }
}
