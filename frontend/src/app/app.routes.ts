import { Routes } from "@angular/router";
import { PollListComponent } from "./pages/poll-list.component";
import { LoginComponent } from "./pages/login.component";
import { RegisterComponent } from "./pages/register.component";
import { CreatePollComponent } from "./pages/create-poll.component";

export const routes: Routes = [
  { path: "", component: PollListComponent },
  { path: "login", component: LoginComponent },
  { path: "register", component: RegisterComponent },
  { path: "create", component: CreatePollComponent },
];
