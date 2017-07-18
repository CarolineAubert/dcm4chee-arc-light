/**
 * Created by shefki on 9/20/16.
 */
import {Component, OnInit, Input, EventEmitter} from '@angular/core';
import {FormGroup} from '@angular/forms';
import {FormService} from '../../helpers/form/form.service';
import {FormElement} from '../../helpers/form/form-element';
import {Output} from '@angular/core';
import {OrderByPipe} from '../../pipes/order-by.pipe';
import * as _ from 'lodash';
import {SearchPipe} from '../../pipes/search.pipe';

@Component({
    selector: 'dynamic-form',
    templateUrl: './dynamic-form.component.html',
    providers: [ FormService ]
})
export class DynamicFormComponent implements OnInit{
    @Input() formelements: FormElement<any>[] = [];
    @Input() model;
    @Output() submitFunction = new EventEmitter<any>();
    @Output() onChangeFunction = new EventEmitter<any>();
    @Input() dontShowSearch;
    @Input() dontShowSave;
    @Input() dontGroup;
    form: FormGroup;
    payLoad = '';
    partSearch = '';
    prevPartSearch = '';
    listStateBeforeSearch: FormElement<any>[];
    filteredFormElements: FormElement<any>[];
    constructor(private formservice: FormService){}
    // submi(){
    //     console.log("in submitfunctiondynamicform");
    //     this.submitFunction.emmit("test");
    // }

    ngOnInit(): void {
        console.log('formelements', this.formelements);
        let orderedGroup: any = new OrderByPipe().transform(this.formelements, 'order');
        let orderValue = 0;
        let order = 0;
        // this.filteredFormElements = _.cloneDeep(this.formelements);
        if(!this.dontGroup){
            _.forEach(orderedGroup, (m, i) => {
                if (orderValue != parseInt(m.order)){
                    let title = '';
                    if (1 <= m.order && m.order < 3){
                        title = 'Extensions';
                        order = 0;
                    }else{
                        if (3 <= m.order && m.order  < 4) {
                            title = 'Child Objects';
                            order = 2;
                        }else{
                            title = 'Attributes';
                            order = 4;
                        }
                    }
                    orderedGroup.splice(i, 0, {
                        controlType: 'togglebutton',
                        title: title,
                        orderId: order,
                        order: order
                    });
                }
                orderValue = parseInt(m.order);
            });
        }
        this.formelements = orderedGroup;
        let formGroup: any = this.formservice.toFormGroup(orderedGroup);
        this.form = formGroup;
        console.log('after convert form', this.form);
        //Test setting some values
        console.log('this.model=', this.model);
/*        if(this.model){
            this.form.patchValue(this.model);
        }*/
        // this.setFormModel(this.model);
        this.form.valueChanges
/*            .debounceTime(500)
            .distinctUntilChanged()*/
            .subscribe(fe => {
                console.log('insubscribe changes fe', fe);
                console.log('form', this.form)
                this.onChangeFunction.emit(this.form);
            });

        console.log('form', this.form);
    }

    filterFormElements(){
        if (this.partSearch != ''){
            if ( (this.partSearch.length === 1 && this.prevPartSearch.length < this.partSearch.length) ||
                (!this.prevPartSearch && !this.listStateBeforeSearch)
            ) {
                this.listStateBeforeSearch = _.cloneDeep(this.formelements);
            }
            this.formelements = new OrderByPipe().transform(this.listStateBeforeSearch, 'order');
            this.formelements = new SearchPipe().transform(this.formelements, this.partSearch);
        }else{
            if (_.size(this.listStateBeforeSearch) > 0){
                this.formelements = _.cloneDeep(this.listStateBeforeSearch);
            }
        }
        this.prevPartSearch = this.partSearch;
    }
    onSubmit(){
        console.log('this.form.value', this.form.value);
        this.payLoad = JSON.stringify(this.form.value);
        this.submitFunction.emit(this.form.value);
    }
    getForm(){
        return this.form;
    }
    setFormModel(model: any){
        this.form.patchValue(model);
    }
    setForm(form: any){
        this.form = form;
    }

}