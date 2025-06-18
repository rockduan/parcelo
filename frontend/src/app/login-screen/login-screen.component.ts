// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { environment } from '../../environments/environment';

@Component({
    selector: 'app-login-screen',
    templateUrl: './login-screen.component.html',
    styleUrl: './login-screen.component.scss',
    standalone: true,
    imports: [MatButtonModule],
})
export class LoginScreenComponent implements OnInit {
    readonly loginUrl = `${environment.developerApiUrl}/auth/github/login`;

    ngOnInit() {
        console.log('LoginScreenComponent loaded');
        console.log('Current URL:', window.location.href);
        console.log('Login URL:', this.loginUrl);
    }
}
