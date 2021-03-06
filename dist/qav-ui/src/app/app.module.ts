import {NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';
import {FormsModule} from '@angular/forms';
import {HttpClientModule} from '@angular/common/http';
import {HashLocationStrategy, LocationStrategy} from '@angular/common';

import {AppComponent} from './app.component';
import {routing} from './app.routing';
import {NodeListComponent} from './node-list/node-list.component';
import {NodeDetailComponent} from './node-detail/node-detail.component';
import {EdgeDetailComponent} from './edge-detail/edge-detail.component';
import {GraphService} from './graph/graph.service';
import {InfoComponent} from './info/info.component';
import {ImageListComponent} from './image-list/image-list.component';
import {ImageDetailComponent} from './image-detail/image-detail.component';


@NgModule({
  declarations: [
    AppComponent,
    NodeListComponent,
    NodeDetailComponent,
    EdgeDetailComponent,
    InfoComponent,
    ImageListComponent,
    ImageDetailComponent
  ],
  imports: [
    BrowserModule,
    FormsModule,
    HttpClientModule,
    routing
  ],
  providers: [
    GraphService,
    {provide: LocationStrategy, useClass: HashLocationStrategy}
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
