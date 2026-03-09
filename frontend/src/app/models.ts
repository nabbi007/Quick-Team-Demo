export interface User {
  id: number;
  email: string;
  fullName: string | null;
  role: string;
  createdAt: string | null;
}

export interface AuthResponse {
  token: string;
  email: string;
  name: string;
  role: string;
}

export interface Department {
  id: number;
  name: string;
}

export interface DepartmentMember {
  id: number;
  email: string;
  department_id: number;
}

export interface Poll {
  id: number;
  question: string;
  description: string | null;
  creator_id: number;
  multi_select: boolean | null;
  anonymous: boolean;
  expires_at: string | null;
  active: boolean | null;
  created_at: string | null;
}

export interface PollOption {
  id: number;
  poll_id: number;
  option_text: string;
  vote_count: number | null;
}

export interface PollInvite {
  id: number;
  poll_id: number;
  department_member_id: number;
  invited_at: string | null;
  vote_status: string | null;
}

export interface Vote {
  id: number;
  poll_id: number;
  option_id: number;
  user_id: number;
  created_at: string | null;
}
