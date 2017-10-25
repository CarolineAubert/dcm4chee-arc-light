import { Injectable } from '@angular/core';
import {AppService} from "../app.service";
import {J4careHttpService} from "./j4care-http.service";
import {Http, ResponseContentType, Headers} from "@angular/http";
import {Subscriber} from "rxjs/Subscriber";
import {Observable} from "rxjs/Observable";
declare var fetch;
@Injectable()
export class j4care {
    header = new Headers();
    constructor(
        public mainservice:AppService,
        public httpJ4car:J4careHttpService,
        public ngHttp:Http
    ) {}
    static traverse(object,func){
        for(let key in object){
            if(object.hasOwnProperty(key)) {
                if(typeof object[key] === "object"){
                    this.traverse(object[key],func);
                }else{
                    object[key] = func.apply(object,[object[key],key]);
                }
            }
        }
        return object;
    }
    static firstLetterToUpperCase(str){
        return str && str[0].toUpperCase() + str.slice(1);
    }
    static firstLetterToLowerCase(str) {
        return str && str[0].toLowerCase() + str.slice(1);
    }

    download(url){
        this.httpJ4car.refreshToken().subscribe((res)=>{
            let token;
            let a = document.createElement('a');

            if(res.length && res.length > 0){
                this.httpJ4car.resetAuthenticationInfo(res);
                token = res.token;
            }else{
                token = this.mainservice.global.authentication.token;
            }
/*            let xhttp = new XMLHttpRequest();
            let filename = url.substring(url.lastIndexOf("/") + 1).split("?")[0];*/
/*            xhttp.onload = function() {
                let a = document.createElement('a');
                a.href = window.URL.createObjectURL(xhttp.response); // xhr.response is a blob
                xhttp.onreadystatechange = null;
                xhttp.abort();
                let attr = document.createAttribute("download");
                // attr.value = "true";
                a.setAttributeNode(attr);
                // a.download = filename; // Set the file name.
                a.style.display = 'none';
                document.body.appendChild(a);
                a.click();
                a.remove();
            };*/
/*            xhttp.onreadystatechange = ()=>{
                if (xhttp.readyState == 4){
                    if ((xhttp.status == 200) || (xhttp.status == 0)){
                        let a = document.createElement('a');
                        a.href = window.URL.createObjectURL(xhttp.response); // xhr.response is a blob
                        let attr = document.createAttribute("download");
                        xhttp.abort();
                        // attr.value = "true";
                        a.setAttributeNode(attr);
                        // a.download = filename; // Set the file name.
                        a.style.display = 'none';
                        document.body.appendChild(a);
                        a.click();
                        a.remove();

                    }
                }
            };
            xhttp.open("GET", url);
            xhttp.responseType = "blob";
            xhttp.setRequestHeader('Authorization', `Bearer ${token}`);
            xhttp.send();*/
            this.header.append('Authorization', `Bearer ${token}`);
/*            this.header.append('Authorization', `Bearer ${token}`);
            this.get(url).subscribe((res)=>{
                a.href = res;
                let attr = document.createAttribute("download");
                a.setAttributeNode(attr);
                // a.download = filename; // Set the file name.
                a.style.display = 'none';
                document.body.appendChild(a);
                a.click();
                a.remove();
            },(err)=>{

            })*/
            console.log("header",this.header);
            this.ngHttp.get(url,{
                        headers:this.header,
                        responseType: ResponseContentType.Blob
                    })
                .map(res => {
                        return new Blob([res['_body']], {
                            type: res.headers.get("Content-Type")
                        });
                })
                .subscribe((myBlob)=> {
                a.href = window.URL.createObjectURL(myBlob);
                let attr = document.createAttribute("download");
                a.setAttributeNode(attr);
                // a.download = filename; // Set the file name.
                a.style.display = 'none';
                document.body.appendChild(a);
                a.click();
                a.remove();
            });
/*            console.log("token",token);
            var myHeaders = new Headers();
            myHeaders.append('Authorization', `Bearer ${token}`);

            var myInit = { method: 'GET',
                headers: myHeaders,
                mode: 'cors',
                cache: 'default' };
            let a = document.createElement('a');;

            fetch(url,myInit)
                .then((response)=> {
                    console.log("response.url",response);
                    return response.blob();
                })
                .then((myBlob)=> {
                    a.href = window.URL.createObjectURL(myBlob);
                    let attr = document.createAttribute("download");
                    a.setAttributeNode(attr);
                    // a.download = filename; // Set the file name.
                    a.style.display = 'none';
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                });*/
        });
    }
    get(url: string): Observable<any> {
        return new Observable((observer: Subscriber<any>) => {
            let objectUrl: string = null;

            this.ngHttp
                .get(url, {
                    headers:this.header,
                    responseType: ResponseContentType.Blob
                })
                .subscribe(m => {
                    objectUrl = URL.createObjectURL(m.blob());
                    observer.next(objectUrl);
                });

            return () => {
                if (objectUrl) {
                    URL.revokeObjectURL(objectUrl);
                    objectUrl = null;
                }
            };
        });
    }

    static flatten(data) {
        var result = {};

        function recurse(cur, prop) {
            if (Object(cur) !== cur) {
                result[prop] = cur;
            } else if (Array.isArray(cur)) {
                for (var i = 0, l = cur.length; i < l; i++)
                    recurse(cur[i], prop + "[" + i + "]");
                if (l == 0) result[prop] = [];
            } else {
                var isEmpty = true;
                for (var p in cur) {
                    isEmpty = false;
                    recurse(cur[p], prop ? prop + "." + p : p);
                }
                if (isEmpty && prop) result[prop] = {};
            }
        }
        recurse(data, "");
        return result;
    };
}
