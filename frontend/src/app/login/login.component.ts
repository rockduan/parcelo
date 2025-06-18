// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../auth.service';

console.log("---LoginComponent loaded");
@Component({
    selector: 'app-login',
    templateUrl: './login.component.html',
    styleUrl: './login.component.scss',
    imports: [MatProgressSpinnerModule],
    standalone: true,
})
export class LoginComponent implements OnInit {
    loading = true;

    constructor(
        private authService: AuthService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
    ) {
	    console.log("LoginComponent constructor");
    }
    ngOnInit(): void {
	console.log("ngOnInit");
        this.activatedRoute.queryParams.subscribe(params => {
            console.log("routes params:", params);
            this.authService.logIn(params['code'], params['state']).subscribe(
                success => {
                    console.log("login result:", success);
                    console.log('localStorage:', {
                        loggedIn: localStorage.getItem('loggedIn'),
                        reviewer: localStorage.getItem('reviewer'),
                        publisher: localStorage.getItem('publisher')
                    });
                    if (success) {
                        console.log("prepareing jump to page apps");
                        this.router.navigate(['apps']);
                    } else {
                        console.log("login failed!");
                        this.loading = false;
                    }
                },
                error => {
                    console.error("Error during login:", error);
                }
            );
        });
    }
}
