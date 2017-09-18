import { Component } from '@angular/core';
import {MdDialogRef} from '@angular/material';
import {AppService} from '../../../app.service';
import {Http} from '@angular/http';
import * as _ from 'lodash';
import {WindowRefService} from "../../../helpers/window-ref.service";

@Component({
    selector: 'app-export',
    templateUrl: './export.component.html',
    styles: [`
        .vex-theme-os.export{
            width:500px;
            
        }
    `]
})
export class ExportDialogComponent{

    private _noDicomExporters;
    private _aes;
    private _dicomPrefixes;
    _ = _;
    private _warning;
    private _title;
    private _okButtonLabel;
    private _externalAetMode;
    private _result = {
        exportType: 'dicom',
        selectedAet: undefined,
        selectedExporter: undefined,
        queue:false,
        dicomPrefix: undefined,
        checkboxes: {
            'only-stgcmt': undefined,
            'only-ian': undefined
        }
    };
    private _preselectedAet;
    constructor(public dialogRef: MdDialogRef<ExportDialogComponent>, private $http: Http, private mainservice: AppService) {
        this.getAes();

        console.log('resultfilter', );
    }

    get preselectedAet() {
        return this._preselectedAet;
    }

    set preselectedAet(value) {
        this._result.selectedAet = value;
        this._preselectedAet = value;
    }
    get result(){
        return this._result;
    }

    set result(value: any) {
        this._result = value;
    }

    get okButtonLabel() {
        return this._okButtonLabel;
    }

    set okButtonLabel(value) {
        this._okButtonLabel = value;
    }

    get warning() {
        return this._warning;
    }

    set warning(value) {
        this._warning = value;
    }

    get title() {
        return this._title;
    }

    set title(value) {
        this._title = value;
    }

    get dicomPrefixes() {
        return this._dicomPrefixes;
    }

    set dicomPrefixes(value) {
        this._dicomPrefixes = value;
    }

    get noDicomExporters() {
        return this._noDicomExporters;
    }

    set noDicomExporters(value) {
        this._noDicomExporters = value;
    }

    get aes() {
        return this._aes;
    }

    set aes(value) {
        this._aes = value;
    }

    get externalInternalAetMode() {
        return this._externalAetMode;
    }

    set externalInternalAetMode(value) {
        this._externalAetMode = value;
    }

    getAes(){
        let $this = this;
        this.$http.get(
            '../aes'
        )
        .map(res => {let resjson; try{ let pattern = new RegExp("[^:]*:\/\/[^\/]*\/auth\/"); if(pattern.exec(res.url)){ WindowRefService.nativeWindow.location = "/dcm4chee-arc/ui2/";} resjson = res.json(); }catch (e){ resjson = [];} return resjson;})
        .subscribe((response) => {
            $this.aes = response;
            $this._result.selectedAet = $this._result.selectedAet || $this.aes[0].dicomAETitle;
            if ($this.mainservice.global && !$this.mainservice.global.aes){
                let global = _.cloneDeep($this.mainservice.global);
                global.aes = response;
                $this.mainservice.setGlobal(global);
            }else{
                if ($this.mainservice.global && $this.mainservice.global.aes){
                    $this.mainservice.global.aes = response;
                }else{
                    $this.mainservice.setGlobal({aes: response});
                }
            }
        }, (response) => {
            // vex.dialog.alert("Error loading aes, please reload the page and try again!");
        });
    }
    validForm(){
        if (this._result.exportType === 'dicom'){
           // if (this._result.dicomPrefix && this._result.selectedAet){
            if (this._result.selectedAet){
                return true;
            }else{
                return false;
            }
        }else{
            if (this._result.selectedExporter){
                return true;
            }else{
                return false;
            }
        }
    }
    dialogKeyHandler(e, dialogRef){
        let code = (e.keyCode ? e.keyCode : e.which);
        console.log('in modality keyhandler', code);
        if (code === 13){
            dialogRef.close('ok');
        }
        if (code === 27){
            dialogRef.close(null);
        }
    };
}
