import { Component, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { PollService } from "../services/poll.service";

@Component({
  selector: "app-poll-list",
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="margin-top:30px">
      <h1>Active Polls</h1>
      @if (loading) {
        <p>Loading polls...</p>
      } @else if (polls.length === 0) {
        <p>No polls yet. Be the first to create one!</p>
      } @else {
        @for (poll of polls; track poll.id) {
          <div class="card">
            <h2>{{ poll.question }}</h2>
            <p style="color:#636e72;font-size:13px">by {{ poll.creatorName }} &bull; {{ poll.totalVotes }} votes</p>
            @for (opt of poll.options; track opt.id) {
              <div style="margin-top:8px">
                <div style="display:flex;justify-content:space-between">
                  <span>{{ opt.text }}</span>
                  <span>{{ opt.percentage | number:'1.0-1' }}%</span>
                </div>
                <div class="progress-bar">
                  <div class="progress-fill" [style.width.%]="opt.percentage"></div>
                </div>
              </div>
            }
          </div>
        }
      }
    </div>
  `
})
export class PollListComponent implements OnInit {
  polls: any[] = [];
  loading = true;

  constructor(private pollService: PollService) {}

  ngOnInit() {
    this.pollService.getAll().subscribe({
      next: (res) => { this.polls = res.content || []; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }
}
